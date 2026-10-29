package com.jibanilji.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.YearMonth

/** 월 달력: 날짜마다 그날 챙길 항목 아이콘을 작게 표시. 날짜를 누르면 onSelect */
class MonthCalendar(ctx: Context) : LinearLayout(ctx) {
    var month: YearMonth = YearMonth.now()
    var selected: LocalDate = LocalDate.now()
    var items: List<Item> = emptyList()
    var onSelect: (LocalDate) -> Unit = {}

    private val title = TextView(ctx)
    private val grid = LinearLayout(ctx).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        setPadding(ctx.dp(8), ctx.dp(4), ctx.dp(8), ctx.dp(4))

        val header = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        fun arrow(s: String, delta: Long) = TextView(ctx).apply {
            text = s; textSize = 20f; gravity = Gravity.CENTER
            setPadding(ctx.dp(16), ctx.dp(6), ctx.dp(16), ctx.dp(6))
            contentDescription = if (delta < 0) "이전 달" else "다음 달"
            setOnClickListener { month = month.plusMonths(delta); render() }
        }
        title.apply {
            textSize = 17f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
            setOnClickListener { // 제목 누르면 오늘로
                month = YearMonth.now(); selected = LocalDate.now(); render(); onSelect(selected)
            }
        }
        header.addView(arrow("‹", -1))
        header.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(arrow("›", 1))
        addView(header)

        val week = LinearLayout(ctx)
        listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
            week.addView(TextView(ctx).apply {
                text = d; textSize = 12f; gravity = Gravity.CENTER
                setTextColor(ctx.getColor(when (i) { 0 -> R.color.danger; 6 -> R.color.brand; else -> R.color.muted }))
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(week)
        addView(grid)
    }

    fun render() {
        title.text = "${month.year}년 ${month.monthValue}월"
        grid.removeAllViews()
        val byDay = items.groupBy { it.due() }
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value % 7 // 일요일 시작
        val rows = (lead + month.lengthOfMonth() + 6) / 7
        val today = LocalDate.now()

        for (r in 0 until rows) {
            val row = LinearLayout(context)
            for (c in 0 until 7) {
                val dayNum = r * 7 + c - lead + 1
                val cell = LinearLayout(context).apply {
                    orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, context.dp(3), 0, context.dp(3))
                }
                if (dayNum in 1..month.lengthOfMonth()) {
                    val date = month.atDay(dayNum)
                    val list = byDay[date].orEmpty()
                    cell.addView(TextView(context).apply {
                        text = dayNum.toString(); textSize = 13f; gravity = Gravity.CENTER
                        if (date == today) {
                            setTypeface(typeface, Typeface.BOLD)
                            setTextColor(context.getColor(R.color.brand))
                        } else if (c == 0) setTextColor(context.getColor(R.color.danger))
                    })
                    cell.addView(TextView(context).apply {
                        textSize = 11f; gravity = Gravity.CENTER; maxLines = 1
                        text = when {
                            list.isEmpty() -> " "
                            list.size <= 2 -> list.joinToString("") { it.icon }
                            else -> list.take(1).joinToString("") { it.icon } + "+${list.size - 1}"
                        }
                    })
                    if (date == selected) cell.background = GradientDrawable().apply {
                        cornerRadius = context.dp(10).toFloat()
                        setColor(context.getColor(R.color.photo_bg))
                    }
                    cell.contentDescription = "${month.monthValue}월 ${dayNum}일, ${list.size}개"
                    cell.setOnClickListener { selected = date; render(); onSelect(date) }
                }
                row.addView(cell, LayoutParams(0, context.dp(50), 1f))
            }
            grid.addView(row)
        }
    }
}
