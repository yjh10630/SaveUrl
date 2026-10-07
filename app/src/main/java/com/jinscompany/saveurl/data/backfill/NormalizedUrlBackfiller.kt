package com.jinscompany.saveurl.data.backfill

import com.jinscompany.saveurl.utils.UrlNormalizer

/** BaseSaveUrl 의 (id, url, normalizedUrl) 투영. Room 쿼리 결과 POJO 로도 사용 */
data class NormalizedUrlRow(
    val id: Int,
    val url: String?,
    val normalizedUrl: String,
)

/** 백필이 DB 에 접근하는 최소 인터페이스 (단위 테스트에서 가짜 구현으로 교체) */
interface NormalizedUrlStore {
    /** id 오름차순으로 [afterId] 보다 큰 행을 최대 [limit] 개. [onlyEmpty] 면 normalizedUrl='' 행만 */
    suspend fun fetchAfter(afterId: Int, onlyEmpty: Boolean, limit: Int): List<NormalizedUrlRow>

    /** 한 묶음을 하나의 트랜잭션으로 반영. 그사이 url 이 바뀐 행은 건너뛴다 */
    suspend fun apply(updates: List<NormalizedUrlRow>)

    /** 마지막으로 전체 재계산을 끝낸 정규화 알고리즘 버전 (없으면 0) */
    suspend fun completedVersion(): Int
    suspend fun setCompletedVersion(version: Int)
}

/**
 * normalizedUrl 백필 (1회성, 멱등).
 *
 * - DB v3→v4 AutoMigration 으로 추가된 기존 행은 normalizedUrl='' 이라 중복 감지가 안 됨
 * - 정규화 알고리즘이 바뀌면([UrlNormalizer.VERSION]) 기존 값과 새 값이 달라 중복 감지가 안 됨
 *
 * 저장된 완료 버전이 현재 VERSION 보다 낮으면 모든 행을 다시 계산하고, 같으면 normalizedUrl='' 인 행만 채운다.
 * 묶음([chunkSize]) 단위로 커밋하고, 전체가 끝난 뒤에만 완료 버전을 기록하므로 중간에 프로세스가 죽어도
 * 다음 실행에서 처음부터 다시 수행하면 된다(같은 값을 다시 쓰는 것뿐이라 안전).
 */
class NormalizedUrlBackfiller(
    private val store: NormalizedUrlStore,
    private val normalize: (String) -> String = UrlNormalizer::normalize,
    private val currentVersion: Int = UrlNormalizer.VERSION,
    private val chunkSize: Int = 200,
) {
    data class Result(val scanned: Int, val updated: Int, val fullRecompute: Boolean)

    suspend fun run(): Result {
        val fullRecompute = store.completedVersion() < currentVersion
        var afterId = Int.MIN_VALUE
        var scanned = 0
        var updated = 0
        while (true) {
            val rows = store.fetchAfter(afterId, onlyEmpty = !fullRecompute, limit = chunkSize)
            if (rows.isEmpty()) break
            scanned += rows.size
            val updates = computeUpdates(rows, normalize)
            if (updates.isNotEmpty()) {
                store.apply(updates)
                updated += updates.size
            }
            afterId = rows.maxOf { it.id }
            if (rows.size < chunkSize) break
        }
        if (fullRecompute) store.setCompletedVersion(currentVersion)
        return Result(scanned, updated, fullRecompute)
    }

    companion object {
        /** 값이 실제로 바뀌는 행만 반환 (불필요한 쓰기 방지). url 이 없으면 '' 유지 */
        fun computeUpdates(rows: List<NormalizedUrlRow>, normalize: (String) -> String): List<NormalizedUrlRow> =
            rows.mapNotNull { row ->
                val url = row.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val value = normalize(url)
                if (value == row.normalizedUrl) null else row.copy(normalizedUrl = value)
            }
    }
}
