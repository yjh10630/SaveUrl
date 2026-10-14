package com.jinscompany.saveurl.domain.importer

/** 판정한 형식 (미리보기 배지, 기록 아이콘) */
enum class ImportFormat {
    /** 카카오톡 대화 내보내기 (Android·iOS·PC) */
    KAKAO,
    /** 브라우저 북마크 HTML (Netscape 형식: Chrome·Edge·웨일·Firefox) */
    BOOKMARK,
    /** 구글 테이크아웃 zip 안의 Chrome 북마크 */
    TAKEOUT,
    /** SaveLink 데이터 백업 (CSV) */
    BACKUP,
    /** 그 밖의 글·HTML·CSV·JSON (링크만 찾음) */
    TEXT,
}

/** [com.jinscompany.saveurl.domain.model.ImportBatch.source] 값 */
fun ImportFormat.sourceKey(): String = name.lowercase()

/**
 * 파일에서 찾은 링크 하나 (파일 안 중복을 합치기 전).
 * 카톡 대화 본문은 담지 않는다. [sender] 는 미리보기 화면에서만 쓰고 저장하지 않는다.
 */
data class ImportCandidate(
    val url: String,
    val title: String? = null,
    /** 원래 날짜 (ms). null = 날짜를 읽지 못함 */
    val date: Long? = null,
    /** 북마크 폴더 (루트 폴더 뺌, 바깥 → 안쪽) 또는 CSV 의 folder 열 */
    val folderPath: List<String> = emptyList(),
    val sender: String? = null,
    // SaveLink 백업만
    val category: String? = null,
    val categoryLocked: Boolean = false,
    val tags: List<String> = emptyList(),
    val isBookmark: Boolean = false,
    /**
     * 가져온 링크는 읽음으로 넣는다 (카톡 대화 수백 개가 모두 "안 읽음"이 되지 않게. 42번 §7.1).
     * SaveLink 백업만 파일의 값을 쓴다 (CsvImportParser).
     */
    val isRead: Boolean = true,
    val imageUrl: String? = null,
    val siteName: String? = null,
    val description: String? = null,
)

/** 파일 하나(또는 zip 안 항목 하나)를 읽은 결과 */
data class ParsedSource(
    val format: ImportFormat,
    /** 파일 이름 (zip 안이면 항목 이름) */
    val fileName: String,
    /** 카톡 헤더의 방 이름 */
    val roomName: String? = null,
    val candidates: List<ImportCandidate>,
    /** http(s) 가 아니라 건너뛴 링크 수 (javascript:, chrome:// 등) */
    val nonHttpCount: Int = 0,
    /** 이 형식에 날짜 정보가 있는지 (일반 글은 없음 → 오늘 날짜) */
    val hasDates: Boolean = false,
    /** CSV 에서 따옴표가 깨져 읽지 못한 줄 수 */
    val skippedRecords: Int = 0,
    /** 대화 기간 (대화 고르기 화면 부제) */
    val firstDate: Long? = null,
    val lastDate: Long? = null,
)

/** 읽기 실패 (오류 화면 종류, 34번 §S11) */
enum class ImportError {
    NO_LINKS, UNSUPPORTED, EMPTY, TOO_BIG, ZIP_EMPTY, TAKEOUT_NO_BOOKMARKS, IO, STORAGE_FULL,
}

class ImportFailure(val error: ImportError, message: String? = null) : Exception(message ?: error.name)

/** 파일 읽기 결과 */
data class ReadOutcome(
    val fileName: String,
    val fileSize: Long?,
    val isZip: Boolean,
    /** 링크가 하나 이상인 항목만 */
    val sources: List<ParsedSource>,
    /** 후보가 [ImportLimits.maxCandidates] 를 넘어 잘렸는지 */
    val truncated: Boolean,
    /** 여러 파일을 공유했을 때 읽지 못한 파일 수 */
    val unreadFiles: Int = 0,
)

/** 안전 상한 (34번 §S11) */
data class ImportLimits(
    val maxFileBytes: Long = 50L * 1024 * 1024,
    val maxZipTotalBytes: Long = 200L * 1024 * 1024,
    val maxZipEntries: Int = 2_000,
    val maxCandidates: Int = 50_000,
)
