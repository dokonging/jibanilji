package com.jibanilji.app

/**
 * 추천 집안일 루틴. 요일은 정하지 않고 빈도만 추천 (예: 매주 1회)
 * 사용자가 고치거나 새로 만든 목록은 계정(users/{uid}.routines)에 저장되고, 없으면 기본값을 씀
 */
object Routines {
    data class Routine(
        val group: String, val name: String, val icon: String,
        val cycle: Int = 1, val unit: String = "주",
    ) {
        fun freqText() = Item(kind = HOME, cycle = cycle, unit = unit).repeatText()
        fun toMap() = mapOf("group" to group, "name" to name, "icon" to icon, "cycle" to cycle, "unit" to unit)

        companion object {
            fun from(m: Map<*, *>?): Routine? {
                if (m == null) return null
                val name = m["name"] as? String ?: return null
                return Routine(
                    group = m["group"] as? String ?: "기타", name = name,
                    icon = m["icon"] as? String ?: "🧹",
                    cycle = (m["cycle"] as? Number)?.toInt() ?: 1,
                    unit = m["unit"] as? String ?: "주",
                )
            }
        }
    }

    val GROUP_ORDER = listOf("청소", "세탁·침구", "교체품", "가전·필터", "쓰레기·분리수거", "식물·반려동물", "관리·납부", "기타")

    private fun g(group: String, vararg rs: Triple<String, String, Pair<Int, String>>) =
        rs.map { (name, icon, f) -> Routine(group, name, icon, f.first, f.second) }

    private infix fun Int.of(unit: String) = this to unit

    val DEFAULTS: List<Routine> =
        g("청소",
            Triple("화장실 청소", "🚽", 1 of "주"),
            Triple("욕실 배수구 청소", "🛁", 2 of "주"),
            Triple("주방 배수구·거름망", "🫧", 1 of "주"),
            Triple("바닥 물걸레", "🧹", 3 of "일"),
            Triple("냉장고 정리", "🧊", 2 of "주"),
            Triple("창틀·방충망 청소", "🪟", 3 of "개월"),
            Triple("침대 밑·가구 뒤 청소", "🛋️", 1 of "개월"),
        ) + g("세탁·침구",
            Triple("이불 빨래", "🛏️", 2 of "주"),
            Triple("베개 커버 세탁", "🛏️", 1 of "주"),
            Triple("세탁조 청소", "🧺", 1 of "개월"),
            Triple("수건 삶기", "🧺", 1 of "개월"),
            Triple("커튼 세탁", "🪟", 6 of "개월"),
            Triple("매트리스 뒤집기", "🛏️", 3 of "개월"),
        ) + g("교체품",
            Triple("칫솔 교체", "🪥", 3 of "개월"),
            Triple("수세미 교체", "🧽", 1 of "개월"),
            Triple("행주 교체", "🧽", 1 of "개월"),
            Triple("수건 교체", "🧺", 1 of "년"),
            Triple("면도날 교체", "🪒", 2 of "주"),
            Triple("샤워기 필터 교체", "🚿", 1 of "개월"),
            Triple("베개 교체", "🛏️", 2 of "년"),
        ) + g("가전·필터",
            Triple("에어컨 필터 청소", "❄️", 2 of "주"),
            Triple("공기청정기 필터 교체", "🌀", 6 of "개월"),
            Triple("정수기 필터 교체", "💧", 6 of "개월"),
            Triple("가습기 세척", "💨", 3 of "일"),
            Triple("로봇청소기 먼지통 비우기", "🤖", 1 of "주"),
            Triple("식기세척기 필터 청소", "🍽️", 1 of "개월"),
            Triple("전자레인지 청소", "✨", 1 of "개월"),
        ) + g("쓰레기·분리수거",
            Triple("분리수거", "♻️", 1 of "주"),
            Triple("음식물 쓰레기", "🗑️", 3 of "일"),
            Triple("일반 쓰레기 버리기", "🗑️", 3 of "일"),
        ) + g("식물·반려동물",
            Triple("화분 물주기", "🪴", 1 of "주"),
            Triple("반려동물 목욕", "🐶", 1 of "개월"),
            Triple("반려동물 구충제", "💊", 3 of "개월"),
            Triple("어항 물갈이", "🐟", 2 of "주"),
        ) + g("관리·납부",
            Triple("관리비 확인", "🧾", 1 of "개월"),
            Triple("보일러 점검", "🔥", 1 of "년"),
            Triple("소화기 점검", "🧯", 1 of "년"),
            Triple("구급상자 약 유통기한 확인", "💊", 6 of "개월"),
        )
}
