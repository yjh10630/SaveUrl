package com.jinscompany.saveurl.data.room

/**
 * 잠금 카테고리 숨김 규칙의 SQL 조각. 링크·휴지통 목록을 읽는 쿼리는 모두 이 조각을 쓴다 ("보이는 링크" 한 경로).
 * 링크와 휴지통은 카테고리를 이름(`category` 열)으로 참조하므로 두 테이블에 같은 조각을 쓸 수 있다.
 * 쿼리에 Category 테이블이 들어가므로 Room PagingSource/Flow 는 카테고리를 잠그거나 풀어도 다시 읽힌다.
 */
object LockSql {
    /** 잠금 카테고리가 아닌 행 (category 가 null 이면 NOT IN 이 null 이 되어 빠지므로 따로 넣는다) */
    const val NOT_LOCKED =
        "(category IS NULL OR category NOT IN (SELECT name FROM Category WHERE isLocked = 1))"

    /** 쿼리 메서드에 `includeLocked: Boolean` 인자가 있어야 한다. true 면 잠금 카테고리 행도 포함 */
    const val VISIBLE = "(:includeLocked = 1 OR $NOT_LOCKED)"
}
