package com.jibanilji.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

/** 구글 계정으로만 로그인 */
class LoginActivity : AppCompatActivity() {
    private lateinit var btn: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "집안일지"
        val (root, col) = column()

        col.text("🏠", 56f, top = 48)
        col.text("집안일지", 28f, bold = true, top = 8)
        col.text("소비기한과 집안일 루틴을\n가족·친구와 함께 챙겨요", 15f, top = 6, color = R.color.muted)
        val err = col.text("", 13f, top = 16, color = R.color.danger)

        btn = col.button("G   Google 계정으로 시작하기", top = 40) {
            err.text = ""
            busy(true)
            lifecycleScope.launch {
                try {
                    val option = GetSignInWithGoogleOption.Builder(getString(R.string.default_web_client_id)).build()
                    val req = GetCredentialRequest.Builder().addCredentialOption(option).build()
                    val cred = CredentialManager.create(this@LoginActivity)
                        .getCredential(this@LoginActivity, req).credential
                    if (cred !is CustomCredential ||
                        cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) { busy(false); err.text = "구글 계정 정보를 받지 못했어요"; return@launch }

                    val google = GoogleIdTokenCredential.createFrom(cred.data)
                    val nick = google.givenName?.takeIf { it.isNotBlank() } ?: google.displayName
                    Repo.auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null))
                        .addOnSuccessListener { proceed(nick, err) }
                        .addOnFailureListener { busy(false); err.text = "로그인하지 못했어요: ${it.message}" }
                } catch (e: GetCredentialCancellationException) {
                    busy(false) // 사용자가 닫음
                } catch (e: NoCredentialException) {
                    busy(false); err.text = "이 폰에 구글 계정이 없어요. 설정 > 계정에서 추가해 주세요"
                } catch (e: GetCredentialException) {
                    busy(false); err.text = "로그인 창을 열지 못했어요: ${e.message}"
                }
            }
        }
        col.text("처음이면 자동으로 가입돼요", 12f, top = 8, color = R.color.muted)
            .gravity = android.view.Gravity.CENTER

        setContentView(root)
    }

    private fun busy(b: Boolean) {
        btn.isEnabled = !b
        btn.text = if (b) "잠시만요…" else "G   Google 계정으로 시작하기"
    }

    private fun proceed(nick: String?, err: android.widget.TextView) {
        Repo.ensureHousehold(this, nick) { hid, e ->
            busy(false)
            if (hid == null) { err.text = "집 정보를 불러오지 못했어요: $e"; return@ensureHousehold }
            startActivity(Intent(this, MainActivity::class.java)); finish()
        }
    }
}
