package com.jibanilji.app

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import org.json.JSONObject
import java.time.LocalDate

/**
 * Firestore 구조
 *   users/{uid}                         { household(지금 보는 집), households[], name, routines[] }
 *   households/{hid}                    { name, code, members[], names{uid:닉네임}, owner, premium }
 *   households/{hid}/items/{id}         식품 · 집안일
 *   households/{hid}/items/{id}/logs/{logId} 완료 기록 { at, by, uid }
 *   invites/{code}                      { hid }
 *
 * 한 사람이 여러 집을 가질 수 있음: 혼자 쓰는 집, 가족과 공유하는 집 등
 */
object Repo {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /** 화면 간 전달용: 최근에 받은 항목들 (사진 포함) */
    var latest: Map<String, Item> = emptyMap()
    /** 지금 보는 집의 구성원 uid → 닉네임 */
    var members: Map<String, String> = emptyMap()
    /** 마지막으로 난 오류 (설정 > 동기화 상태에 표시) */
    var lastError: String? = null

    fun uid() = auth.currentUser?.uid
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("repo", 0)
    fun cachedHid(ctx: Context) = prefs(ctx).getString("hid", null)
    fun nick(ctx: Context) = prefs(ctx).getString("nick", null) ?: "나"

    /** 내 집 목록 (hid → 이름), 기기에 저장해두고 알림·전환에 사용 */
    fun houses(ctx: Context): Map<String, String> {
        val o = JSONObject(prefs(ctx).getString("houses", "{}")!!)
        return o.keys().asSequence().associateWith { o.getString(it) }
    }
    private fun saveHouses(ctx: Context, m: Map<String, String>) =
        prefs(ctx).edit().putString("houses", JSONObject(m).toString()).apply()
    fun houseName(ctx: Context, hid: String) = houses(ctx)[hid] ?: "우리 집"

    private fun remember(ctx: Context, hid: String?, nick: String? = null) {
        prefs(ctx).edit().apply {
            putString("hid", hid); if (nick != null) putString("nick", nick)
        }.apply()
    }

    private fun userRef() = db.collection("users").document(uid()!!)
    private fun householdRef(hid: String) = db.collection("households").document(hid)
    fun itemsRef(hid: String) = householdRef(hid).collection("items")
    private fun logsRef(hid: String, id: String) = itemsRef(hid).document(id).collection("logs")

    // ---------- 집 ----------
    /** 로그인 직후: 내 집들 확인, 하나도 없으면 새로 만들기 */
    fun ensureHousehold(ctx: Context, nickIfNew: String?, done: (String?, String?) -> Unit) {
        if (uid() == null) return done(null, "로그인이 필요해요")
        userRef().get()
            .addOnSuccessListener { u ->
                val nick = u.getString("name") ?: nickIfNew?.takeIf { it.isNotBlank() }
                    ?: auth.currentUser?.email?.substringBefore('@') ?: "나"
                prefs(ctx).edit().putString("nick", nick).apply()
                val list = (u.get("households") as? List<*>)?.map { it.toString() }
                    ?: listOfNotNull(u.getString("household"))
                val cur = u.getString("household")?.takeIf { it in list } ?: list.firstOrNull()
                if (cur == null) createHousehold(ctx, "${nick}네 집") { hid, e -> done(hid, e) }
                else {
                    remember(ctx, cur)
                    if (u.get("households") == null) userRef().set(mapOf("households" to list), SetOptions.merge())
                    loadHouseNames(ctx, list) { done(cur, null) }
                }
            }
            .addOnFailureListener { done(null, explain(it)) }
    }

    /** 집 이름들을 받아와 기기에 저장 */
    fun loadHouseNames(ctx: Context, list: List<String>? = null, done: () -> Unit = {}) {
        val ids = list ?: houses(ctx).keys.toList()
        if (ids.isEmpty()) return done()
        Tasks.whenAllComplete(ids.map { householdRef(it).get() }).addOnCompleteListener { t ->
            val m = linkedMapOf<String, String>()
            t.result.forEachIndexed { i, task ->
                @Suppress("UNCHECKED_CAST")
                val doc = (task as Task<com.google.firebase.firestore.DocumentSnapshot>)
                    .takeIf { it.isSuccessful }?.result
                if (doc != null && doc.exists()) m[ids[i]] = doc.getString("name") ?: "우리 집"
            }
            saveHouses(ctx, m)
            done()
        }
    }

    /** 새 집 만들기 → 그 집으로 전환 */
    fun createHousehold(ctx: Context, name: String, retry: Int = 2, done: (String?, String?) -> Unit) {
        val uid = uid() ?: return done(null, "로그인이 필요해요")
        val nick = nick(ctx)
        val h = db.collection("households").document()
        val code = randomCode()
        db.batch()
            .set(h, mapOf(
                "name" to name, "code" to code, "owner" to uid,
                "members" to listOf(uid), "names" to mapOf(uid to nick),
            ))
            .set(db.collection("invites").document(code), mapOf("hid" to h.id))
            .set(userRef(), mapOf(
                "household" to h.id, "name" to nick, "households" to FieldValue.arrayUnion(h.id),
            ), SetOptions.merge())
            .commit()
            .addOnSuccessListener {
                remember(ctx, h.id)
                saveHouses(ctx, houses(ctx) + (h.id to name))
                done(h.id, null)
            }
            .addOnFailureListener {
                if (retry > 0) createHousehold(ctx, name, retry - 1, done) else done(null, explain(it))
            }
    }

    fun switchTo(ctx: Context, hid: String) {
        remember(ctx, hid)
        if (uid() != null) userRef().set(mapOf("household" to hid), SetOptions.merge())
    }

    private fun randomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // 헷갈리는 0/O/1/I 제외
        return (1..6).map { chars.random() }.joinToString("")
    }

    /** 지금 보는 집 정보 실시간: 구성원 이름 · 프리미엄 여부 */
    fun listenHousehold(ctx: Context, hid: String, onError: (String) -> Unit = {}, cb: (Map<String, Any?>) -> Unit): ListenerRegistration =
        householdRef(hid).addSnapshotListener { snap, e ->
            if (e != null) onError(explain(e))
            val data = snap?.data ?: return@addSnapshotListener
            members = (data["names"] as? Map<*, *>)?.entries
                ?.associate { it.key.toString() to it.value.toString() } ?: emptyMap()
            Premium.active = data["premium"] == true
            (data["name"] as? String)?.let { saveHouses(ctx, houses(ctx) + (hid to it)) }
            cb(data)
        }

    fun renameHousehold(ctx: Context, hid: String, name: String) {
        householdRef(hid).update("name", name)
        saveHouses(ctx, houses(ctx) + (hid to name))
    }

    /** 초대 코드로 다른 집에 참여 (내 다른 집들은 그대로) */
    fun join(ctx: Context, rawCode: String, done: (Boolean, String) -> Unit) {
        val uid = uid() ?: return done(false, "로그인이 필요해요")
        val code = rawCode.trim().uppercase()
        if (code.length != 6) return done(false, "6자리 코드를 입력해 주세요")
        val nick = nick(ctx)
        db.collection("invites").document(code).get()
            .addOnSuccessListener { inv ->
                val hid = inv.getString("hid") ?: return@addOnSuccessListener done(false, "코드를 찾을 수 없어요")
                if (hid in houses(ctx)) return@addOnSuccessListener done(false, "이미 함께 쓰고 있는 집이에요")
                householdRef(hid)
                    .update(mapOf("members" to FieldValue.arrayUnion(uid), "names.$uid" to nick))
                    .addOnSuccessListener {
                        userRef().set(mapOf(
                            "household" to hid, "name" to nick, "households" to FieldValue.arrayUnion(hid),
                        ), SetOptions.merge())
                        remember(ctx, hid)
                        loadHouseNames(ctx, houses(ctx).keys.toList() + hid) {
                            done(true, "'${houseName(ctx, hid)}'에 참여했어요!")
                        }
                    }
                    .addOnFailureListener { done(false, "참여하지 못했어요: ${explain(it)}") }
            }
            .addOnFailureListener { done(false, "코드를 확인하지 못했어요: ${explain(it)}") }
    }

    /** 집 나가기. 혼자 쓰던 집이면 그 집의 모든 데이터를 지움 */
    fun leaveHouse(ctx: Context, hid: String, done: (Boolean, String) -> Unit) {
        val uid = uid() ?: return done(false, "로그인이 필요해요")
        val rest = houses(ctx).keys.filter { it != hid }
        if (rest.isEmpty()) return done(false, "집이 하나뿐이라 나갈 수 없어요. 새 집을 먼저 만들어 주세요")
        fun finish() {
            userRef().set(mapOf(
                "households" to FieldValue.arrayRemove(hid),
                "household" to rest.first(),
            ), SetOptions.merge())
            saveHouses(ctx, houses(ctx) - hid)
            Mirror.remove(ctx, hid)
            if (cachedHid(ctx) == hid) remember(ctx, rest.first())
            Alarms.scheduleAll(ctx, Mirror.all(ctx))
            done(true, "나갔어요")
        }
        householdRef(hid).get().addOnSuccessListener { h ->
            val ms = (h.get("members") as? List<*>)?.size ?: 0
            if (ms > 1) leave(hid, uid).addOnCompleteListener { finish() }
            else wipeHouse(hid, h.getString("code")) { finish() }
        }.addOnFailureListener { done(false, "나가지 못했어요: ${explain(it)}") }
    }

    private fun leave(hid: String, uid: String) =
        householdRef(hid).update(mapOf("members" to FieldValue.arrayRemove(uid), "names.$uid" to FieldValue.delete()))

    /** 집의 기록 · 항목 · 초대 코드 · 집 삭제 (순서 중요: 집을 먼저 지우면 권한 확인이 안 됨) */
    private fun wipeHouse(hid: String, code: String?, done: () -> Unit) {
        itemsRef(hid).get().addOnSuccessListener { items ->
            val logGets = items.documents.map { logsRef(hid, it.id).get() }
            Tasks.whenAllComplete(logGets).addOnCompleteListener {
                val refs = logGets.filter { it.isSuccessful }
                    .flatMap { t -> t.result.documents.map { it.reference } } +
                    items.documents.map { it.reference }
                Tasks.whenAllComplete(refs.map { it.delete() }).addOnCompleteListener {
                    val inv: Task<Void> = code?.let { db.collection("invites").document(it).delete() }
                        ?: Tasks.forResult(null)
                    inv.addOnCompleteListener {
                        householdRef(hid).delete().addOnCompleteListener { done() }
                    }
                }
            }
        }.addOnFailureListener { done() }
    }

    // ---------- 항목 ----------
    fun listen(hid: String, onError: (String) -> Unit = {}, cb: (List<Item>) -> Unit): ListenerRegistration =
        itemsRef(hid).addSnapshotListener { snap, e ->
            if (e != null) { onError(explain(e)); return@addSnapshotListener }
            if (snap == null) return@addSnapshotListener
            val items = snap.documents.map { Item.from(it.id, it.data ?: emptyMap()) }
            latest = items.associateBy { it.id }
            cb(items)
        }

    /** 다른 집들의 목록도 받아와 알림 맞추기 (지금 보는 집 외) */
    fun syncOtherHouses(ctx: Context) {
        val cur = cachedHid(ctx)
        houses(ctx).keys.filter { it != cur }.forEach { hid ->
            itemsRef(hid).get().addOnSuccessListener { s ->
                Mirror.save(ctx, hid, s.documents.map { Item.from(it.id, it.data ?: emptyMap()) })
                Alarms.scheduleAll(ctx, Mirror.all(ctx))
            }
        }
    }

    /** 오프라인이어도 바로 반영되고, 연결되면 자동 업로드 */
    fun save(hid: String, item: Item, ctx: Context? = null) {
        val ref = if (item.id.isEmpty()) itemsRef(hid).document() else itemsRef(hid).document(item.id)
        item.id = ref.id
        val app = ctx?.applicationContext
        ref.set(item.toMap()).addOnFailureListener { e ->
            app?.toast("저장하지 못했어요: ${explain(e)}")
            lastError = explain(e)
        }
    }

    fun explain(e: Exception): String {
        val code = (e as? FirebaseFirestoreException)?.code?.name
        return when (code) {
            "PERMISSION_DENIED" -> "권한 없음 (Firestore 규칙을 확인해 주세요)"
            "NOT_FOUND" -> "데이터베이스를 찾지 못했어요 (Firestore를 만들었는지 확인)"
            "UNAVAILABLE" -> "인터넷 연결이 불안정해요"
            "FAILED_PRECONDITION" -> "Firestore 설정이 완료되지 않았어요"
            null -> e.message ?: "알 수 없는 오류"
            else -> "$code: ${e.message}"
        }
    }

    fun delete(hid: String, id: String) {
        logsRef(hid, id).get().addOnSuccessListener { s -> s.documents.forEach { it.reference.delete() } }
        itemsRef(hid).document(id).delete()
    }

    /** 집안일 완료: 오늘 날짜로 갱신 + 재고 1개 사용 + 일지에 기록 */
    fun complete(ctx: Context, hid: String, item: Item) {
        item.base = LocalDate.now().toEpochDay()
        item.qty?.let { if (it > 0) item.qty = it - 1 }
        save(hid, item, ctx)
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

    // ---------- 추천 루틴 (계정에 저장, 없으면 기본값) ----------
    fun loadRoutines(done: (List<Routines.Routine>) -> Unit) {
        if (uid() == null) return done(Routines.DEFAULTS)
        userRef().get().addOnSuccessListener { u ->
            val raw = u.get("routines") as? List<*>
            done(raw?.mapNotNull { Routines.Routine.from(it as? Map<*, *>) } ?: Routines.DEFAULTS)
        }.addOnFailureListener { done(Routines.DEFAULTS) }
    }

    fun saveRoutines(list: List<Routines.Routine>?) {
        if (uid() == null) return
        userRef().set(mapOf("routines" to (list?.map { it.toMap() } ?: FieldValue.delete())), SetOptions.merge())
    }

    // ---------- 계정 ----------
    fun signOut(ctx: Context) {
        auth.signOut()
        remember(ctx, null)
        saveHouses(ctx, emptyMap())
        latest = emptyMap(); members = emptyMap(); Premium.active = false
        Mirror.clear(ctx)
        Alarms.scheduleAll(ctx, emptyList())
    }

    /** 계정 삭제 (Play 스토어 필수). 내 집들에서 빠지고, 혼자 쓰던 집은 데이터도 삭제 */
    fun deleteAccount(ctx: Context, done: (Boolean, String) -> Unit) {
        val user = auth.currentUser ?: return done(false, "로그인이 필요해요")
        val uid = user.uid
        val ids = houses(ctx).keys.toList()

        fun finishAuth() {
            user.delete()
                .addOnSuccessListener { signOut(ctx); done(true, "계정을 삭제했어요") }
                .addOnFailureListener { e ->
                    if (e is FirebaseAuthRecentLoginRequiredException) {
                        signOut(ctx); done(false, "보안을 위해 다시 로그인한 뒤 한 번 더 삭제해 주세요")
                    } else done(false, "삭제하지 못했어요: ${e.message}")
                }
        }

        fun next(i: Int) {
            if (i >= ids.size) {
                db.collection("users").document(uid).delete().addOnCompleteListener { finishAuth() }
                return
            }
            val hid = ids[i]
            householdRef(hid).get().addOnSuccessListener { h ->
                val ms = (h.get("members") as? List<*>)?.size ?: 0
                if (ms > 1) leave(hid, uid).addOnCompleteListener { next(i + 1) }
                else wipeHouse(hid, h.getString("code")) { next(i + 1) }
            }.addOnFailureListener { next(i + 1) }
        }
        next(0)
    }
}
