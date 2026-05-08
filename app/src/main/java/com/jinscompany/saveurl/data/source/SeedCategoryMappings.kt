package com.jinscompany.saveurl.data.source

object SeedCategoryMappings {
    val domainToCategory: Map<String, String> = mapOf(
        // 개발
        "github.com" to "개발",
        "stackoverflow.com" to "개발",
        "developer.android.com" to "개발",
        "developer.apple.com" to "개발",
        "docs.kotlinlang.org" to "개발",
        "kotlinlang.org" to "개발",
        "developer.mozilla.org" to "개발",
        "medium.com" to "개발",
        "velog.io" to "개발",
        "tistory.com" to "개발",
        "devdocs.io" to "개발",
        "npmjs.com" to "개발",
        "pub.dev" to "개발",
        "gradle.org" to "개발",
        "jetbrains.com" to "개발",

        // 뉴스
        "news.naver.com" to "뉴스",
        "news.daum.net" to "뉴스",
        "chosun.com" to "뉴스",
        "donga.com" to "뉴스",
        "hani.co.kr" to "뉴스",
        "yna.co.kr" to "뉴스",
        "bbc.com" to "뉴스",
        "cnn.com" to "뉴스",
        "reuters.com" to "뉴스",
        "bloomberg.com" to "뉴스",
        "techcrunch.com" to "뉴스",
        "zdnet.co.kr" to "뉴스",

        // 쇼핑
        "coupang.com" to "쇼핑",
        "shopping.naver.com" to "쇼핑",
        "smartstore.naver.com" to "쇼핑",
        "gmarket.co.kr" to "쇼핑",
        "11st.co.kr" to "쇼핑",
        "amazon.com" to "쇼핑",
        "auction.co.kr" to "쇼핑",
        "lotteon.com" to "쇼핑",
        "ssg.com" to "쇼핑",
        "musinsa.com" to "패션",
        "29cm.co.kr" to "패션",
        "kream.co.kr" to "패션",

        // 동영상/미디어
        "youtube.com" to "영상",
        "youtu.be" to "영상",
        "netflix.com" to "영상",
        "twitch.tv" to "영상",
        "vimeo.com" to "영상",
        "tving.com" to "영상",
        "wavve.com" to "영상",

        // 음식/레시피
        "10000recipe.com" to "음식",
        "mangoplate.com" to "음식",
        "siksin.com" to "음식",
        "delicious.com" to "음식",

        // 여행
        "tripadvisor.com" to "여행",
        "airbnb.com" to "여행",
        "booking.com" to "여행",
        "agoda.com" to "여행",
        "naver.com/travel" to "여행",

        // 금융
        "finance.naver.com" to "금융",
        "investing.com" to "금융",
        "coinmarketcap.com" to "금융",
        "upbit.com" to "금융",
        "bithumb.com" to "금융",
    )
}
