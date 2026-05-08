package com.jinscompany.saveurl.data.source

object CategoryKeywordDictionary {
    val keywordToCategory: Map<String, String> = mapOf(
        // 개발
        "kotlin" to "개발", "android" to "개발", "swift" to "개발", "java" to "개발",
        "python" to "개발", "javascript" to "개발", "typescript" to "개발", "react" to "개발",
        "compose" to "개발", "github" to "개발", "api" to "개발", "sdk" to "개발",
        "코틀린" to "개발", "안드로이드" to "개발", "개발" to "개발", "프로그래밍" to "개발",
        "라이브러리" to "개발", "프레임워크" to "개발", "알고리즘" to "개발",

        // 뉴스
        "뉴스" to "뉴스", "속보" to "뉴스", "이슈" to "뉴스", "사회" to "뉴스",
        "정치" to "뉴스", "경제" to "뉴스", "국제" to "뉴스", "breaking" to "뉴스",

        // 쇼핑
        "쇼핑" to "쇼핑", "구매" to "쇼핑", "할인" to "쇼핑", "판매" to "쇼핑",
        "상품" to "쇼핑", "배송" to "쇼핑", "리뷰" to "쇼핑",

        // 음식
        "레시피" to "음식", "요리" to "음식", "맛집" to "음식", "식당" to "음식",
        "recipe" to "음식", "food" to "음식", "restaurant" to "음식",

        // 여행
        "여행" to "여행", "호텔" to "여행", "숙박" to "여행", "관광" to "여행",
        "travel" to "여행", "hotel" to "여행", "tour" to "여행",

        // 영상
        "유튜브" to "영상", "동영상" to "영상", "영상" to "영상", "강의" to "영상",
        "video" to "영상", "youtube" to "영상",

        // 금융
        "주식" to "금융", "코인" to "금융", "투자" to "금융", "비트코인" to "금융",
        "crypto" to "금융", "stock" to "금융", "bitcoin" to "금융",
    )

    fun suggest(title: String, description: String): String? {
        val combined = "$title $description".lowercase()
        return keywordToCategory.entries
            .firstOrNull { (keyword, _) -> combined.contains(keyword) }
            ?.value
    }
}
