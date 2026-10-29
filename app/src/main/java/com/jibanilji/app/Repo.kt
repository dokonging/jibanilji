package com.jibanilji.app

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.time.LocalDate

/**
 * Firestore 구조
 *   users/{uid}                         { household, name }
 *   households/{hid}                    { name, code, members[], names{uid:닉네임}, owner, premium }
 *   households/{hid}/items/{id}         식품 · 집안일
 *   households/{hid}/items/{id}/logs/{logId} 완료 기록 { at, by, uid }
 *   invites/{code}                      { hid }
 */
object Repo {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /** 화면 간 전달용: 최근에 받은 항목들 (사진 포함) */
    var latest: Map<String, Item> = emptyMap()
    /** 함께 쓰는 사람 uid → 닉네임 */
    var members: Map<String, String> = emptyMap()

    fun uid() = auth.currentUser?.uid
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("repo", 0)
    fun cachedHid(ctx: Context) = prefs(ctx).getString("hid", null)
    fun nick(ctx: Context) = prefs(ctx).getString("nick", null) ?: "나"
    private fun remember(ctx: Context, hid: String?, nick: String? = null) {
        prefs(ctx).edit().apply {
            putString("hid", hid); if (nick != null) putString("nick", nick)
        }.apply()
    }

    private fun householdRef(hid: String) = db.collection("households").document(hid)
    fun itemsRef(hid: String) = householdRef(hid).collection("items")
    private fun logsRef(hid: String, id: String) = itemsRef(hid).document(id).collection("logs")

    // ---------- 가구(살림 공간) ----------
    fun ensureHousehold(ctx: Context, nickIfNew: String?, done: (String?, String?) -> Unit) {
        val uid = uid() ?: return done(null, "로그인이 필요해요")
        db.collection("users").document(uid).get()
            .addOnSuccessListener { u ->
                val nick = u.getString("name") ?: nickIfNew?.takeIf { it.isNotBlank() }
                    ?: auth.currentUser?.email?.substringBefore('@') ?: "나"
                val hid = u.getString("household")
                if (hid != null) { remember(ctx, hid, nick); done(hid, null) }
                else createHousehold(ctx, uid, nick, 2, done)
            }
            .addOnFailureListener { done(null, it.message) }
    }

    private fun createHousehold(ctx: Context, uid: String, nick: String, retry: Int, done: (String?, String?) -> Unit) {
        val h = db.collection("households").document()
        val code = randomCode()
        db.batch()
            .set(h, mapOf(
                "name" to "${nick}네 집", "code" to code, "owner" to uid,
                "members" to listOf(uid), "names" to mapOf(uid to nick),
            ))
            .set(db.collection("invites").document(code), mapOf("hid" to h.id))
            .set(db.collection("users").document(uid), mapOf("household" to h.id, "name" to nick))
            .commit()
            .addOnSuccessListener { remember(ctx, h.id, nick); done(h.id, null) }
            .addOnFailureListener {
                if (retry > 0) createHousehold(ctx, uid, nick, retry - 1, done) else done(null, it.message)
            }
    }

    private fun randomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // 헷갈리는 0/O/1/I 제외
        return (1..6).map { chars.random() }.joinToString("")
    }

    /** 가구 정보 실시간: 구성원 이름 · 프리미엄 여부 */
    fun listenHousehold(hid: String, cb: (Map<String, Any?>) -> Unit): ListenerRegistration =
        householdRef(hid).addSnapshotListener { snap, _ ->
            val data = snap?.data ?: return@addSnapshotListener
            members = (data["names"] as? Map<*, *>)?.entries
                ?.associate { it.key.toString() to it.value.toString() } ?: emptyMap()
            Premium.active = data["premium"] == true
            cb(data)
        }

    fun household(hid: String, done: (Map<String, Any?>?) -> Unit) {
        householdRef(hid).get().addOnSuccessListener { done(it.data) }.addOnFailureListener { done(null) }
    }

    fun renameHousehold(hid: String, name: String) { householdRef(hid).update("name", name) }

    /** 초대 코드로 다른 집에 참여 (기존 집에서는 빠짐) */
    fun join(ctx: Context, rawCode: String, done: (Boolean, String) -> Unit) {
        val uid = uid() ?: return done(false, "로그인이 필요해요")
        val code = rawCode.trim().uppercase()
        if (code.length != 6) return done(false, "6자리 코드를 입력해 주세요")
        val nick = nick(ctx)
        val old = cachedHid(ctx)
        db.collection("invites").document(code).get()
            .addOnSuccessListener { inv ->
                val hid = inv.getString("hid") ?: return@addOnSuccessListener done(false, "코드를 찾을 수 없어요")
                if (hid == old) return@addOnSuccessListener done(false, "이미 함께 쓰고 있어요")
                householdRef(hid)
                    .update(mapOf("members" to FieldValue.arrayUnion(uid), "names.$uid" to nick))
                    .addOnSuccessListener {
                        db.collection("users").document(uid)
                            .set(mapOf("household" to hid, "name" to nick), SetOptions.merge())
                        if (old != null) leave(old, uid)
                        remember(ctx, hid)
                        done(true, "참여했어요! 이제 같은 목록을 함께 봐요")
                    }
                    .addOnFailureListener { done(false, "참여하지 못했어요: ${it.message}") }
            }
            .addOnFailureListener { done(false, "코드를 확인하지 못했어요: ${it.message}") }
    }

    private fun leave(hid: String, uid: String) =
        householdRef(hid).update(mapOf("members" to FieldValue.arrayRemove(uid), "names.$uid" to FieldValue.delete()))

    // ---------- 항목 ----------
    fun listen(hid: String, cb: (List<Item>) -> Unit): ListenerRegistration =
        itemsRef(hid).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            val items = snap.documents.map { Item.from(it.id, it.data ?: emptyMap()) }
            latest = items.associateBy { it.id }
            cb(items)
        }

    /** 오프라인이어도 바로 반영되고, 연결되면 자동 업로드 */
    fun save(hid: String, item: Item) {
        val ref = if (item.id.isEmpty()) itemsRef(hid).document() else itemsRef(hid).document(item.id)
        item.id = ref.id
        ref.set(item.toMap())
    }

    fun delete(hid: String, id: String) {
        logsRef(hid, id).get().addOnSuccessListener { s -> s.documents.forEach { it.reference.delete() } }
        itemsRef(hid).document(id).delete()
    }

    /** 집안일 완료: 오늘 날짜로 갱신 + 재고 1개 사용 + 일지에 기록 */
    fun complete(ctx: Context, hid: String, item: Item) {
        item.base = LocalDate.now().toEpochDay()
        item.qty?.let { if (it > 0) item.qty = it - 1 }
        save(hid, item)
        logsRef(hid, item.id).add(mapOf(
            "at" to System.currentTimeMillis(), "by" to nick(ctx), "uid" to (uid() ?: ""),
        ))
    }

    fun logs(hid: String, id: String, done: (List<LogEntry>) -> Unit) {
        logsRef(hid, id).orderBy("at", Query.Direction.DESCENDING).limit(30).get()
            .addOnSuccessListener { s ->
                done(s.documents.map {
                    LogEntry((it.get("at") as? Number)?.toLong() ?: 0L, it.getString("by") ?: "")
                })
            }
            .addOnFailureListener { done(emptyList()) }
    }

    // ---------- 계정 ----------
    fun signOut(ctx: Context) {
        auth.signOut()
        remember(ctx, null)
        latest = emptyMap(); members = emptyMap(); Premium.active = false
        Mirror.save(ctx, emptyList())
        Alarms.scheduleAll(ctx, emptyList())
    }

    /** 계정 삭제 (Play 스토어 필수). 혼자 쓰던 집이면 데이터도 함께 삭제 */
    fun deleteAccount(ctx: Context, done: (Boolean, String) -> Unit) {
        val user = auth.currentUser ?: return done(false, "로그인이 필요해요")
        val uid = user.uid
        val hid = cachedHid(ctx)

        fun finishAuth() {
            user.delete()
                .addOnSuccessListener { signOut(ctx); done(true, "계정을 삭제했어요") }
                .addOnFailureListener { e ->
                    if (e is FirebaseAuthRecentLoginRequiredException) {
                        signOut(ctx); done(false, "보안을 위해 다시 로그인한 뒤 한 번 더 삭제해 주세요")
                    } else done(false, "삭제하지 못했어요: ${e.message}")
                }
        }

        fun dropUser() {
            db.collection("users").document(uid).delete()
                .addOnCompleteListener { finishAuth() }
        }

        if (hid == null) return dropUser()
        householdRef(hid).get().addOnSuccessListener { h ->
            val ms = (h.get("members") as? List<*>)?.size ?: 0
            if (ms > 1) { leave(hid, uid).addOnCompleteListener { dropUser() }; return@addOnSuccessListener }
            // 혼자 쓰던 집: 항목 · 기록 · 초대 코드 · 집 삭제
            // 순서: 기록·항목 → 초대 코드 → 집 (집을 먼저 지우면 권한 확인이 안 돼서)
            itemsRef(hid).get().addOnSuccessListener { items ->
                val logGets = items.documents.map { logsRef(hid, it.id).get() }
                Tasks.whenAllComplete(logGets).addOnCompleteListener {
                    val refs = logGets.filter { it.isSuccessful }
                        .flatMap { t -> t.result.documents.map { it.reference } } +
                        items.documents.map { it.reference }
                    Tasks.whenAllComplete(refs.map { it.delete() }).addOnCompleteListener {
                        val code = h.getString("code")
                        val inv: Task<Void> = code?.let { db.collection("invites").document(it).delete() }
                            ?: Tasks.forResult(null)
                        inv.addOnCompleteListener {
                            householdRef(hid).delete().addOnCompleteListener { dropUser() }
                        }
                    }
                }
            }.addOnFailureListener { dropUser() }
        }.addOnFailureListener { dropUser() }
    }
}
