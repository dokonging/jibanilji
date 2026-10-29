package com.jibanilji.app

import android.content.Context
import androidx.appcompat.app.AlertDialog

/**
 * 유료 기능 (가구 단위: 한 명이 결제하면 함께 쓰는 사람 모두 적용)
 *  - 같이 쓰기 (초대 · 참여)
 *  - 식품 / 집안일 각각 20개 초과 등록
 *
 * ENABLED = false 인 동안은 모두 무료. Play 스토어 출시 + 결제 연동 시 true 로 변경.
 * premium 값은 households/{hid}.premium 에 서버(결제 확인)만 쓸 수 있도록 보안 규칙으로 막아둠.
 */
object Premium {
    const val ENABLED = false
    const val FREE_LIMIT = 20

    var active = false

    fun unlocked() = !ENABLED || active

    fun canAdd(items: Collection<Item>, kind: String, adding: Int = 1) =
        unlocked() || items.count { it.kind == kind } + adding <= FREE_LIMIT

    fun upsell(ctx: Context, reason: String) {
        AlertDialog.Builder(ctx)
            .setTitle("✨ 집안일지 프리미엄")
            .setMessage(
                "$reason\n\n" +
                    "• 가족·친구와 같이 쓰기\n" +
                    "• 식품 · 집안일 무제한 등록\n\n" +
                    "한 명이 결제하면 함께 쓰는 모두에게 적용돼요."
            )
            .setPositiveButton("확인", null)
            .show()
    }
}
