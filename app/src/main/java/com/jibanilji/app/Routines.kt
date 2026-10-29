package com.jibanilji.app

import android.content.Context
import android.graphics.Typeface
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.time.LocalDate

/** 추천 집안일 루틴 (권장 주기는 일반적인 기준, 등록 후 자유롭게 수정) */
object Routines {
    data class Routine(
        val name: String, val icon: String,
        val cycle: Int = 1, val unit: String = "주", val days: List<Int> = emptyList(),
    )

    // days: 1=월 … 7=일
    val GROUPS = listOf(
        "청소" to listOf(
            Routine("화장실 청소", "🚽", 1, "주"),
            Routine("욕실 배수구 청소", "🛁", 2, "주"),
            Routine("주방 배수구·거름망", "🫧", 1, "주"),
            Routine("바닥 물걸레", "🧹", 3, "일"),
            Routine("냉장고 정리", "🧊", 2, "주"),
            Routine("창틀·방충망 청소", "🪟", 3, "개월"),
            Routine("침대 밑·가구 뒤 청소", "🛋️", 1, "개월"),
        ),
        "세탁·침구" to listOf(
            Routine("이불 빨래", "🛏️", 2, "주"),
            Routine("베개 커버 세탁", "🛏️", 1, "주"),
            Routine("세탁조 청소", "🧺", 1, "개월"),
            Routine("수건 삶기", "🧺", 1, "개월"),
            Routine("커튼 세탁", "🪟", 6, "개월"),
            Routine("매트리스 뒤집기", "🛏️", 3, "개월"),
        ),
        "교체품" to listOf(
            Routine("칫솔 교체", "🪥", 3, "개월"),
            Routine("수세미 교체", "🧽", 1, "개월"),
            Routine("행주 교체", "🧽", 1, "개월"),
            Routine("수건 교체", "🧺", 1, "년"),
            Routine("면도날 교체", "🪒", 2, "주"),
            Routine("샤워기 필터 교체", "🚿", 1, "개월"),
            Routine("베개 교체", "🛏️", 2, "년"),
        ),
        "가전·필터" to listOf(
            Routine("에어컨 필터 청소", "❄️", 2, "주"),
            Routine("공기청정기 필터 교체", "🌀", 6, "개월"),
            Routine("정수기 필터 교체", "💧", 6, "개월"),
            Routine("가습기 세척", "💨", 3, "일"),
            Routine("로봇청소기 먼지통 비우기", "🤖", 1, "주"),
            Routine("식기세척기 필터 청소", "🍽️", 1, "개월"),
            Routine("전자레인지 청소", "✨", 1, "개월"),
        ),
        "쓰레기·분리수거" to listOf(
            Routine("분리수거", "♻️", days = listOf(2)),
            Routine("음식물 쓰레기", "🗑️", 3, "일"),
            Routine("일반 쓰레기 버리기", "🗑️", days = listOf(1, 4)),
        ),
        "식물·반려동물" to listOf(
            Routine("화분 물주기", "🪴", 1, "주"),
            Routine("반려동물 목욕", "🐶", 1, "개월"),
            Routine("반려동물 구충제", "💊", 3, "개월"),
            Routine("어항 물갈이", "🐟", 2, "주"),
        ),
        "관리·납부" to listOf(
            Routine("관리비 확인", "🧾", 1, "개월"),
            Routine("보일러 점검", "🔥", 1, "년"),
            Routine("소화기 점검", "🧯", 1, "년"),
            Routine("구급상자 약 유통기한 확인", "💊", 6, "개월"),
        ),
    )

    /** 체크한 루틴을 한 번에 추가 (오늘 시작, 하루 전·당일 오전 9시 알림) */
    fun showPicker(ctx: Context, hid: String, current: Collection<Item>) {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(20), ctx.dp(8), ctx.dp(20), ctx.dp(8))
        }
        val have = current.filter { it.kind == HOME }.map { it.name }.toSet()
        val boxes = mutableListOf<Pair<CheckBox, Routine>>()
        for ((group, list) in GROUPS) {
            col.addView(TextView(ctx).apply {
                text = group; textSize = 13f; setTypeface(typeface, Typeface.BOLD)
                setTextColor(ctx.getColor(R.color.muted))
                setPadding(0, ctx.dp(12), 0, ctx.dp(2))
            })
            list.forEach { r ->
                val sample = Item(kind = HOME, cycle = r.cycle, unit = r.unit,
                    repeat = if (r.days.isEmpty()) "interval" else "days", days = r.days)
                val already = r.name in have
                val cb = CheckBox(ctx).apply {
                    text = "${r.icon}  ${r.name}  ·  ${sample.repeatText()}" + if (already) " (등록됨)" else ""
                    isEnabled = !already
                }
                col.addView(cb); boxes += cb to r
            }
        }

        AlertDialog.Builder(ctx)
            .setTitle("추천 루틴에서 고르기")
            .setView(ScrollView(ctx).apply { addView(col) })
            .setPositiveButton("추가") { _, _ ->
                val picked = boxes.filter { it.first.isChecked }.map { it.second }
                if (picked.isEmpty()) return@setPositiveButton
                if (!Premium.canAdd(current, HOME, picked.size)) {
                    Premium.upsell(ctx, "무료로는 집안일을 ${Premium.FREE_LIMIT}개까지 등록할 수 있어요.")
                    return@setPositiveButton
                }
                val today = LocalDate.now()
                picked.forEach { r ->
                    val byDays = r.days.isNotEmpty()
                    Repo.save(hid, Item(
                        kind = HOME, name = r.name, icon = r.icon,
                        color = Thumb.colorFor(HOME, r.icon),
                        // 요일 반복은 '어제' 기준으로 두면 오늘부터 해당 요일이 바로 잡힘
                        base = (if (byDays) today.minusDays(1) else today).toEpochDay(),
                        cycle = r.cycle, unit = r.unit,
                        repeat = if (byDays) "days" else "interval", days = r.days,
                        hour = 9, minute = 0, offsets = listOf(1, 0), by = Repo.nick(ctx),
                    ))
                }
                ctx.toast("${picked.size}개 추가했어요. 눌러서 주기·알림을 바꿀 수 있어요")
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
