package com.jibanilji.app

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.YearMonth

/** 월 달력 카드: 날짜마다 그날 챙길 항목 아이콘을 작게 표시. 날짜를 누르면 onSelect */
class MonthCalendar(ctx: Context) : LinearLayout(ctx) {
    var month: YearMonth = YearMonth.now()
    var selected: LocalDate = LocalDate.now()
    var items: List<Item> = emptyList()
    var onSelect: (LocalDate) -> Unit = {}

    private val title = TextView(ctx)
    private val grid = LinearLayout(ctx).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        background = ctx.rounded(ctx.c(R.color.surface), 24)
        setPadding(ctx.dp(10), ctx.dp(10), ctx.dp(10), ctx.dp(10))

        val header = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        fun arrow(s: String, delta: Long) = TextView(ctx).apply {
            text = s; textSize = 22f; gravity = Gravity.CENTER
            setTextColor(ctx.c(R.color.muted))
            contentDescription = if (delta < 0) "이전 달" else "다음 달"
            setOnClickListener { month = month.plusMonths(delta); render() }
        }
        title.apply {
            textSize = 18f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
            setTextColor(ctx.c(R.color.text))
            setOnClickListener { // 제목 누르면 오늘로
                month = YearMonth.now(); selected = LocalDate.now(); render(); onSelect(selected)
            }
        }
        header.addView(arrow("‹", -1), LayoutParams(ctx.dp(48), ctx.dp(44)))
        header.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(arrow("›", 1), LayoutParams(ctx.dp(48), ctx.dp(44)))
        addView(header)

        val week = LinearLayout(ctx).apply { setPadding(0, ctx.dp(4), 0, ctx.dp(4)) }
        listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
            week.addView(TextView(ctx).apply {
                text = d; textSize = 12f; gravity = Gravity.CENTER
                setTextColor(ctx.c(when (i) { 0 -> R.color.danger; else -> R.color.muted }))
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(week)
        addView(grid)
    }

    fun render() {
        title.text = "${month.year}년 ${month.monthValue}월"
        grid.removeAllViews()
        val byDay = items.groupBy { it.due() }
        val lead = month.atDay(1).dayOfWeek.value % 7 // 일요일 시작
        val rows = (lead + month.lengthOfMonth() + 6) / 7
        val today = LocalDate.now()
        val ctx = context

        for (r in 0 until rows) {
            val row = LinearLayout(ctx)
            for (col in 0 until 7) {
                val dayNum = r * 7 + col - lead + 1
                val cell = LinearLayout(ctx).apply {
                    orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, ctx.dp(4), 0, ctx.dp(2))
                }
                if (dayNum in 1..month.lengthOfMonth()) {
                    val date = month.atDay(dayNum)
                    val list = byDay[date].orEmpty()
                    val isSel = date == selected
                    cell.addView(TextView(ctx).apply {
                        text = dayNum.toString(); textSize = 14f; gravity = Gravity.CENTER
                        when {
                            isSel -> {
                                setTextColor(ctx.c(R.color.on_brand)); setTypeface(typeface, Typeface.BOLD)
                                background = ctx.rounded(ctx.c(R.color.brand), 14)
                            }
                            date == today -> {
                                setTextColor(ctx.c(R.color.brand)); setTypeface(typeface, Typeface.BOLD)
                                background = ctx.rounded(ctx.c(R.color.brand_soft), 14)
                            }
                            col == 0 -> setTextColor(ctx.c(R.color.danger))
                            else -> setTextColor(ctx.c(R.color.text))
                        }
                    }, LayoutParams(ctx.dp(30), ctx.dp(28)))
                    cell.addView(TextView(ctx).apply {
                        textSize = 11f; gravity = Gravity.CENTER; maxLines = 1
                        setTextColor(ctx.c(R.color.muted))
                        text = when {
                            list.isEmpty() -> " "
                            list.size <= 2 -> list.joinToString("") { it.icon }
                            else -> list.first().icon + "+${list.size - 1}"
                        }
                    })
                    cell.contentDescription = "${month.monthValue}월 ${dayNum}일, ${list.size}개"
                    cell.setOnClickListener { selected = date; render(); onSelect(date) }
                }
                row.addView(cell, LayoutParams(0, ctx.dp(54), 1f))
            }
            grid.addView(row)
        }
    }
}
