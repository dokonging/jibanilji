package com.jibanilji.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.R as A

/** 함께 쓰기: 초대 코드 보기/공유, 코드로 참여, 로그아웃 */
// 스토어 출시 전에 실제 개인정보처리방침 주소로 바꿔주세요 (예: 노션·블로그 공개 페이지)
const val PRIVACY_URL = "https://example.com/privacy"

class ShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "함께 쓰기"
        val hid = Repo.cachedHid(this) ?: return finish()
        val (root, col) = column()

        val name = col.text("불러오는 중…", 22f, bold = true)
        val members = col.text("", 14f, top = 4, color = R.color.muted)

        col.text("초대 코드", 13f, top = 24, color = R.color.muted)
        val codeView = col.text("······", 34f, bold = true, color = R.color.brand)
        col.text("가족·친구가 집안일지 앱에서 이 코드를 입력하면\n같은 목록과 알림을 함께 써요", 13f, top = 4, color = R.color.muted)
        var code = ""

        col.button("📤  초대 메시지 보내기") {
            if (code.isEmpty()) return@button
            if (!Premium.unlocked()) return@button Premium.upsell(this, "같이 쓰기는 프리미엄 기능이에요.")
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT,
                    "집안일지 같이 써요! 🏠\n앱에서 [함께 쓰기 > 다른 집에 참여하기]에 아래 코드를 입력해 주세요\n\n$code")
            }, "초대하기"))
        }
        col.button("코드 복사", A.attr.borderlessButtonStyle, top = 0) {
            if (code.isEmpty()) return@button
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("초대 코드", code))
            toast("복사했어요")
        }
        col.button("집 이름 바꾸기", A.attr.borderlessButtonStyle, top = 0) {
            val et = EditText(this).apply { setText(name.text) }
            AlertDialog.Builder(this).setTitle("집 이름").setView(et)
                .setPositiveButton("저장") { _, _ ->
                    val n = et.text.toString().trim()
                    if (n.isNotEmpty()) { Repo.renameHousehold(hid, n); name.text = n }
                }.setNegativeButton("취소", null).show()
        }

        col.text("다른 집에 참여하기", 17f, bold = true, top = 32)
        col.text("받은 초대 코드를 입력하면 그 집으로 옮겨가요.\n지금 목록은 지금 집에 그대로 남아요", 13f, top = 4, color = R.color.muted)
        val input = col.input("초대 코드 6자리", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        val msg = col.text("", 13f, top = 4, color = R.color.danger)
        col.button("참여하기") {
            if (!Premium.unlocked()) return@button Premium.upsell(this, "같이 쓰기는 프리미엄 기능이에요.")
            msg.text = "확인 중…"
            Repo.join(this, input.text.toString()) { ok, m ->
                if (ok) { toast(m); finish() } else msg.text = m
            }
        }

        col.text("내 계정", 17f, bold = true, top = 32)
        col.text("${Repo.nick(this)} · ${Repo.auth.currentUser?.email ?: ""}", 14f, top = 4, color = R.color.muted)
        col.button("로그아웃", A.attr.borderlessButtonStyle, top = 4) {
            AlertDialog.Builder(this).setMessage("로그아웃할까요? 이 폰에서는 알림도 꺼져요")
                .setPositiveButton("로그아웃") { _, _ ->
                    Repo.signOut(this)
                    startActivity(Intent(this, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }.setNegativeButton("취소", null).show()
        }

        col.button("계정 삭제", A.attr.borderlessButtonStyle, top = 0) {
            AlertDialog.Builder(this).setTitle("계정을 삭제할까요?")
                .setMessage("계정과 내 정보가 지워져요. 혼자 쓰던 집이면 모든 목록과 기록도 함께 지워지고, 되돌릴 수 없어요.")
                .setPositiveButton("삭제") { _, _ ->
                    Repo.deleteAccount(this) { ok, m ->
                        toast(m)
                        if (ok || Repo.uid() == null) startActivity(Intent(this, LoginActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    }
                }.setNegativeButton("취소", null).show()
        }
        col.text("개인정보처리방침", 13f, top = 16, color = R.color.muted).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_URL)))
        }

        setContentView(root)

        Repo.household(hid) { h ->
            if (h == null) { name.text = "집 정보를 불러오지 못했어요"; return@household }
            name.text = h["name"] as? String ?: "우리 집"
            code = h["code"] as? String ?: ""
            codeView.text = code
            val names = (h["names"] as? Map<*, *>)?.values?.map { it.toString() } ?: emptyList()
            members.text = "함께 쓰는 사람 ${names.size}명 · ${names.joinToString(", ")}"
        }
    }
}
