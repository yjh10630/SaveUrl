package com.jinscompany.saveurl.domain.importer

import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.MetaState
import com.jinscompany.saveurl.utils.TrackingParamStripper
import com.jinscompany.saveurl.utils.UrlNormalizer

/**
 * 미리보기 한 줄 (파일 안 중복을 합친 뒤). [key] = 정규화 URL.
 * 카톡 보낸 사람은 화면에만 쓰고 저장하지 않는다.
 */
data class PreviewItem(
    val key: String,
    /** 저장할 URL (추적 파라미터 제거) */
    val url: String,
    val domain: String,
    /** 화면용: 스킴을 뗀 URL */
    val displayUrl: String,
    val title: String?,
    /** 가장 이른 날짜. null = 못 읽음 */
    val date: Long?,
    val sender: String?,
    /** 카테고리 묶음 키 (북마크 폴더, 대화, 백업 카테고리). null = 묶음 없음 */
    val groupKey: String?,
    /** 이미 저장된 링크 (선택할 수 없음) */
    val exists: Boolean,
    val candidate: ImportCandidate,
)

/**
 * 카테고리 묶음 (폴더마다/대화마다/백업 카테고리마다). [defaultName] 이 null 이면 "폴더 없는 링크"(미분류 고정).
 */
data class ImportGroup(
    val key: String,
    val defaultName: String?,
    /** 같은 이름의 폴더가 여러 경로에서 합쳐졌을 때 부제에 보일 경로 */
    val pathLabel: String?,
    val itemCount: Int,
)

/** 미리보기 숫자 (34번 §S5 숫자 정의) */
data class PreviewCounts(
    /** 합치기 전 http(s) 링크 수 */
    val raw: Int,
    /** 파일 안 중복을 합친 수 */
    val found: Int,
    /** found 중 이미 저장된 링크 */
    val existing: Int,
    /** raw - found */
    val inFileDuplicates: Int,
    /** http(s) 가 아니라 건너뛴 링크 */
    val nonHttp: Int,
)

/** 카테고리를 정하는 방식 */
sealed interface CategoryMode {
    /** 모두 한 카테고리에 ([name] 이 "미분류" 면 미분류) */
    data class Single(val name: String) : CategoryMode
    /** 폴더마다 / 대화마다 / 백업 카테고리 그대로 */
    data object PerGroup : CategoryMode
    data object Uncategorized : CategoryMode
}

/** 묶음 하나의 설정 (폴더 편집 시트) */
data class GroupRule(
    val included: Boolean = true,
    /** null = 미분류로 */
    val targetName: String?,
)

/** 미리보기 전체 (파싱 결과 + 중복 판단). 고르기 상태는 [ImportSelection] */
data class ImportPreview(
    val format: ImportFormat,
    /** 고른 대화(소스) 수. 2개 이상이면 "대화마다" */
    val sourceCount: Int,
    val label: String,
    val roomName: String?,
    val items: List<PreviewItem>,
    val groups: List<ImportGroup>,
    val counts: PreviewCounts,
    /** 보낸 사람별 링크 수 (개수 내림차순). 1명 이하면 비움 */
    val senders: List<Pair<String, Int>>,
    val hasDates: Boolean,
    val truncated: Boolean,
    /** 목록에 제목이 있는지 (북마크·백업) */
    val hasTitles: Boolean,
) {
    val firstDate: Long? get() = items.mapNotNull { it.date }.minOrNull()
    val lastDate: Long? get() = items.mapNotNull { it.date }.maxOrNull()
}

/** 미리보기에서 사용자가 고른 상태 */
data class ImportSelection(
    val selectedSenders: Set<String>,
    val categoryMode: CategoryMode,
    val groupRules: Map<String, GroupRule>,
    val useOriginalDate: Boolean,
    /** 직접 해제한 링크 (key) */
    val unchecked: Set<String> = emptySet(),
)

/** 저장할 링크 하나 */
data class ImportRecord(
    val url: String,
    val normalizedUrl: String,
    val title: String?,
    val imageUrl: String?,
    val siteName: String?,
    val description: String?,
    val addDate: Long,
    /** null = 미분류 */
    val category: String?,
    val tags: List<String>,
    val isBookmark: Boolean,
    val isRead: Boolean,
    val metaState: Int,
)

/** 저장 요청 */
data class ImportCommitRequest(
    val format: ImportFormat,
    val label: String,
    val records: List<ImportRecord>,
    /** 백업 파일에서 잠금 카테고리였던 카테고리 (없으면 잠금 카테고리로 만들고, 있으면 잠근다. 37번 8.2) */
    val lockCategories: Set<String>,
    /** 미리보기 이후 바뀐 게 없으면 미리보기에서 센 "이미 있음" 수. 완료 화면 "건너뛴 링크" 에 더한다 */
    val skippedBefore: Int,
)

object ImportPlanner {

    private const val MAX_CATEGORY_NAME = 20

    /**
     * 파싱 결과 → 미리보기. [existingKeys] = DB 의 정규화 URL 전체.
     */
    fun buildPreview(
        sources: List<ParsedSource>,
        existingKeys: Set<String>,
        label: String,
        truncated: Boolean,
    ): ImportPreview {
        val multi = sources.size > 1
        val format = when {
            sources.size == 1 -> sources.first().format
            sources.all { it.format == ImportFormat.KAKAO } -> ImportFormat.KAKAO
            else -> ImportFormat.TEXT
        }
        val byKey = LinkedHashMap<String, PreviewItem>()
        var raw = 0
        var nonHttp = 0
        sources.forEachIndexed { index, source ->
            nonHttp += source.nonHttpCount
            for (c in source.candidates) {
                if (!UrlExtractor.isHttpUrl(c.url)) { nonHttp++; continue }
                raw++
                val cleaned = TrackingParamStripper.strip(c.url.trim())
                val key = UrlNormalizer.normalize(cleaned)
                val groupKey = when {
                    multi -> "source:$index"
                    source.format == ImportFormat.BACKUP -> c.category?.let { "category:$it" }
                    c.folderPath.isNotEmpty() -> "folder:${c.folderPath.last()}"
                    else -> null
                }
                val existing = byKey[key]
                if (existing == null) {
                    byKey[key] = PreviewItem(
                        key = key,
                        url = cleaned,
                        domain = domainOf(cleaned),
                        displayUrl = cleaned.substringAfter("://"),
                        title = c.title,
                        date = c.date,
                        sender = c.sender,
                        groupKey = groupKey,
                        exists = key in existingKeys,
                        candidate = c,
                    )
                } else if (c.date != null && (existing.date == null || c.date < existing.date)) {
                    // 가장 이른 날짜 (처음 보낸 날 = 처음 저장한 날). 보낸 사람·묶음도 그 메시지 기준
                    byKey[key] = existing.copy(date = c.date, sender = c.sender ?: existing.sender)
                }
            }
        }
        val items = byKey.values.toList()
        val groups = buildGroups(sources, items, multi)
        val senderCounts = items.mapNotNull { it.sender }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
        return ImportPreview(
            format = format,
            sourceCount = sources.size,
            label = label,
            roomName = sources.singleOrNull()?.roomName,
            items = items,
            groups = groups,
            counts = PreviewCounts(
                raw = raw,
                found = items.size,
                existing = items.count { it.exists },
                inFileDuplicates = raw - items.size,
                nonHttp = nonHttp,
            ),
            senders = if (senderCounts.size >= 2) senderCounts else emptyList(),
            hasDates = sources.any { it.hasDates },
            truncated = truncated,
            hasTitles = items.any { !it.title.isNullOrBlank() },
        )
    }

    private fun buildGroups(sources: List<ParsedSource>, items: List<PreviewItem>, multi: Boolean): List<ImportGroup> {
        val counts = items.filter { !it.exists }.groupingBy { it.groupKey }.eachCount()
        val result = ArrayList<ImportGroup>()
        if (multi) {
            sources.forEachIndexed { index, s ->
                val key = "source:$index"
                result.add(ImportGroup(key, defaultSourceCategory(s), pathLabel = s.fileName, itemCount = counts[key] ?: 0))
            }
        } else {
            val source = sources.firstOrNull()
            // 같은 이름 폴더가 여러 경로에서 합쳐졌는지 (부제 "새 카테고리 · 경로")
            val paths = HashMap<String, MutableSet<String>>()
            source?.candidates?.forEach { c ->
                if (c.folderPath.isNotEmpty()) paths.getOrPut(c.folderPath.last()) { LinkedHashSet() }.add(c.folderPath.joinToString(" › "))
            }
            items.mapNotNull { it.groupKey }.distinct().forEach { key ->
                val name = key.substringAfter(':')
                val pathLabel = paths[name]?.takeIf { it.size > 1 }?.joinToString(", ")
                result.add(ImportGroup(key, name.ifBlank { null }, pathLabel, counts[key] ?: 0))
            }
            if (items.any { it.groupKey == null } && result.isNotEmpty()) {
                result.add(ImportGroup(NO_GROUP_KEY, null, null, counts[null] ?: 0))
            }
        }
        return result
    }

    /** 묶음 없는 링크 (폴더 없는 링크) 의 키 */
    const val NO_GROUP_KEY = "none"

    /** 카톡 대화 카테고리 기본 이름: `카톡 {방 이름}` (20자에서 자름), 못 읽으면 "카톡에서 가져옴" */
    fun defaultSourceCategory(source: ParsedSource): String = when (source.format) {
        ImportFormat.KAKAO -> source.roomName?.takeIf { it.isNotBlank() }?.let { clip("카톡 $it") } ?: KAKAO_FALLBACK
        else -> clip(source.fileName.substringBeforeLast('.').ifBlank { source.fileName })
    }

    const val KAKAO_FALLBACK = "카톡에서 가져옴"
    const val BOOKMARK_SINGLE_DEFAULT = "가져온 북마크"

    fun clip(name: String): String = name.trim().take(MAX_CATEGORY_NAME).trim()

    /** 처음 미리보기의 기본 선택 (34번 §3.5.2) */
    fun defaultSelection(preview: ImportPreview): ImportSelection {
        val rules = preview.groups.associate { g -> g.key to GroupRule(included = true, targetName = g.defaultName) }
        val mode: CategoryMode = when {
            preview.sourceCount > 1 -> CategoryMode.PerGroup
            preview.format == ImportFormat.BACKUP -> if (preview.groups.isEmpty()) CategoryMode.Uncategorized else CategoryMode.PerGroup
            preview.format == ImportFormat.KAKAO ->
                CategoryMode.Single(preview.roomName?.takeIf { it.isNotBlank() }?.let { clip("카톡 $it") } ?: KAKAO_FALLBACK)
            preview.groups.isEmpty() -> CategoryMode.Uncategorized
            preview.groups.count { it.defaultName != null } > 30 -> CategoryMode.Single(BOOKMARK_SINGLE_DEFAULT)
            else -> CategoryMode.PerGroup
        }
        return ImportSelection(
            selectedSenders = preview.senders.map { it.first }.toSet(),
            categoryMode = mode,
            groupRules = rules,
            useOriginalDate = true,
        )
    }

    /** 보낸 사람·묶음 포함 여부로 목록에 보이는지 (이미 있음 포함) */
    fun isShown(item: PreviewItem, preview: ImportPreview, selection: ImportSelection): Boolean {
        if (preview.senders.isNotEmpty() && item.sender != null && item.sender !in selection.selectedSenders) return false
        if (selection.categoryMode == CategoryMode.PerGroup) {
            val key = item.groupKey ?: NO_GROUP_KEY
            if (selection.groupRules[key]?.included == false) return false
        }
        return true
    }

    /** 가져올 링크인지 (버튼 숫자 = 목록 머리 숫자) */
    fun isSelected(item: PreviewItem, preview: ImportPreview, selection: ImportSelection): Boolean =
        !item.exists && item.key !in selection.unchecked && isShown(item, preview, selection)

    fun selectedItems(preview: ImportPreview, selection: ImportSelection): List<PreviewItem> =
        preview.items.filter { isSelected(it, preview, selection) }

    /** 묶음이 갈 카테고리 이름. null = 미분류 */
    fun targetCategory(item: PreviewItem, selection: ImportSelection): String? = when (val mode = selection.categoryMode) {
        is CategoryMode.Single -> normalizeName(mode.name)
        CategoryMode.Uncategorized -> null
        CategoryMode.PerGroup -> {
            val key = item.groupKey ?: NO_GROUP_KEY
            if (key == NO_GROUP_KEY) null else normalizeName(selection.groupRules[key]?.targetName)
        }
    }

    /** 빈 이름·"미분류" → 미분류(null) */
    fun normalizeName(name: String?): String? {
        val n = name?.trim().orEmpty()
        return if (n.isEmpty() || n == CategoryModel.UNCATEGORIZED) null else n
    }

    /** 이번에 쓰일 카테고리 이름 (미분류 제외, 처음 나온 순서) */
    fun targetCategories(preview: ImportPreview, selection: ImportSelection): List<String> =
        selectedItems(preview, selection).mapNotNull { targetCategory(it, selection) }.distinct()

    /** 저장 요청 만들기. [now] = 오늘 날짜로 저장할 때의 시각 */
    fun buildRequest(preview: ImportPreview, selection: ImportSelection, now: Long): ImportCommitRequest {
        val items = selectedItems(preview, selection)
        val records = items.map { item ->
            val c = item.candidate
            val title = c.title?.takeIf { it.isNotBlank() }
            val image = c.imageUrl?.takeIf { it.isNotBlank() }
            ImportRecord(
                url = item.url,
                normalizedUrl = item.key,
                title = title,
                imageUrl = image,
                siteName = c.siteName?.takeIf { it.isNotBlank() },
                description = c.description?.takeIf { it.isNotBlank() },
                addDate = if (selection.useOriginalDate) item.date ?: now else now,
                category = targetCategory(item, selection),
                tags = c.tags,
                isBookmark = c.isBookmark,
                isRead = c.isRead,
                metaState = when {
                    title != null && image != null -> MetaState.DONE
                    title != null -> MetaState.PENDING_IMAGE
                    else -> MetaState.PENDING
                },
            )
        }
        // 백업에서 잠금이었던 카테고리: 그 카테고리 그대로 넣을 때만 잠근다 (다른 이름으로 바꿨으면 잠그지 않음)
        val lock = items.filter { it.candidate.categoryLocked }
            .mapNotNull { item -> targetCategory(item, selection)?.takeIf { it == item.candidate.category } }
            .toSet()
        return ImportCommitRequest(
            format = preview.format,
            label = preview.label,
            records = records,
            lockCategories = lock,
            skippedBefore = preview.counts.existing,
        )
    }

    /** 날짜를 못 읽은 링크 수 (날짜 정보가 있는 출처에서만 의미) */
    fun dateMissCount(preview: ImportPreview, selection: ImportSelection): Int =
        if (!preview.hasDates) 0 else selectedItems(preview, selection).count { it.date == null }

    internal fun domainOf(url: String): String =
        TrackingParamStripper.extractHost(url.substringBefore('?').substringBefore('#'))?.removePrefix("www.") ?: url
}
