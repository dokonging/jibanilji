package com.jibanilji.app

import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val FOOD = "food"
const val HOME = "home"

/** 식품 보관 방식 */
val STORAGES = listOf("냉장", "냉동", "상온")
fun storageEmoji(s: String) = when (s) { "냉장" -> "🧊"; "냉동" -> "❄️"; "상온" -> "🏠"; else -> "" }

private val DAY_NAMES = listOf("월", "화", "수", "목", "금", "토", "일") // DayOfWeek 1..7

/**
 * food: base = 소비기한(epochDay), qty = 수량, volume = 용량, storage = 냉장/냉동/상온
 * home(집안일): base = 마지막으로 한 날(epochDay)
 *   repeat = "interval" → cycle/unit 마다 (예: 3개월마다)
 *   repeat = "days"     → days 요일마다 (예: 매주 화·금)
 *   qty = 재고 (null이면 재고 기록 안 함), assignee = 담당자 uid (빈 값이면 모두)
 */
data class Item(
    var id: String = "",
    var kind: String = FOOD,
    var name: String = "",
    var photo: String? = null,
    var icon: String = "🥛",
    var color: Int = 0,
    var base: Long = LocalDate.now().toEpochDay(),
    var cycle: Int = 3,
    var unit: String = "개월",
    var repeat: String = "interval",
    var days: List<Int> = emptyList(),
    var hour: Int = 9,
    var minute: Int = 0,
    var offsets: List<Int> = listOf(3, 1, 0),
    var qty: Int? = null,
    var volume: String = "",
    var storage: String = "",
    var assignee: String = "",
    var assigneeName: String = "",
    var by: String = "",
) {
    val byDays get() = kind == HOME && repeat == "days" && days.isNotEmpty()

    /** 알림 기준일: 식품은 소비기한, 집안일은 다음 할 날 */
    fun due(): LocalDate {
        val d = LocalDate.ofEpochDay(base)
        if (kind != HOME) return d
        if (byDays) {
            var x = d.plusDays(1)
            while (x.dayOfWeek.value !in days) x = x.plusDays(1)
            return x
        }
        val n = cycle.toLong()
        return when (unit) {
            "일" -> d.plusDays(n)
            "주" -> d.plusWeeks(n)
            "년" -> d.plusYears(n)
            else -> d.plusMonths(n)
        }
    }

    fun repeatText() = when {
        byDays && days.size == 7 -> "매일"
        byDays -> "매주 " + days.sorted().joinToString("·") { DAY_NAMES[it - 1] }
        cycle == 1 -> when (unit) { "일" -> "매일"; "주" -> "매주 1회"; "개월" -> "매달 1회"; else -> "매년 1회" }
        else -> "${cycle}${unit}마다"
    }

    fun subText(): String {
        val f = DateTimeFormatter.ofPattern("M.d")
        val parts = mutableListOf<String>()
        if (kind == HOME) {
            parts += "${repeatText()} · 다음 ${due().format(f)}"
            if (assigneeName.isNotBlank()) parts += "담당 $assigneeName"
            qty?.let { parts += if (it > 0) "재고 ${it}개" else "⚠️ 재고 없음" }
        } else {
            if (storage.isNotBlank()) parts += storageEmoji(storage) + storage
            parts += "${due().format(f)}까지"
            if (volume.isNotBlank()) parts += volume
            qty?.let { parts += "${it}개" }
        }
        return parts.joinToString(" · ")
    }

    fun toMap(withPhoto: Boolean = true): Map<String, Any?> = buildMap {
        put("kind", kind); put("name", name); put("icon", icon); put("color", color)
        put("base", base); put("cycle", cycle); put("unit", unit)
        put("repeat", repeat); put("days", days)
        put("hour", hour); put("minute", minute); put("offsets", offsets)
        put("qty", qty); put("volume", volume); put("storage", storage)
        put("assignee", assignee); put("assigneeName", assigneeName); put("by", by)
        if (withPhoto) put("photo", photo)
    }

    companion object {
        fun from(id: String, m: Map<String, Any?>): Item {
            fun int(k: String) = (m[k] as? Number)?.toInt()
            fun str(k: String) = m[k] as? String ?: ""
            fun ints(k: String) = (m[k] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
            return Item(
                id = id,
                kind = (m["kind"] as? String) ?: FOOD,
                name = str("name"),
                photo = (m["photo"] as? String)?.ifEmpty { null },
                icon = (m["icon"] as? String) ?: "🥛",
                color = int("color") ?: 0,
                base = (m["base"] as? Number)?.toLong() ?: LocalDate.now().toEpochDay(),
                cycle = int("cycle") ?: 3,
                unit = (m["unit"] as? String) ?: "개월",
                repeat = (m["repeat"] as? String) ?: "interval",
                days = ints("days"),
                hour = int("hour") ?: 9,
                minute = int("minute") ?: 0,
                offsets = ints("offsets"),
                qty = int("qty"),
                volume = str("volume"),
                storage = str("storage"),
                assignee = str("assignee"),
                assigneeName = str("assigneeName"),
                by = str("by"),
            )
        }
    }
}

/** 완료 기록 한 줄 */
data class LogEntry(val at: Long, val by: String)
