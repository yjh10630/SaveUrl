package com.jinscompany.saveurl.ui.save_screen

import com.jinscompany.saveurl.domain.model.MetaState
import com.jinscompany.saveurl.domain.model.UrlData

/**
 * 수정 화면 저장값 정하기 (39번 독립 리뷰 R1).
 *
 * 수정 화면은 열 때 읽은 행을 들고 있다. 그사이 미리보기 채우기가 제목·썸네일을 채웠을 수 있으므로,
 * 저장 직전에 다시 읽은 행([fresh])을 기준으로 사용자가 실제로 바꾼 칸만 덮어쓴다.
 *
 * - 제목·설명·썸네일·사이트 이름: 사용자가 바꾼 칸만 [edited] 값, 나머지는 [fresh] 값
 * - 채우기 상태
 *   - 아무것도 안 바꿈: [fresh] 상태 그대로 (기다리던 링크는 계속 채워진다)
 *   - 제목을 바꿨고 썸네일은 아직 기다림: [MetaState.PENDING_IMAGE] (제목은 지키고 썸네일만 채움)
 *   - 그 밖: [MetaState.DONE]
 * - 카테고리·태그·즐겨찾기는 [edited] 값, 읽음은 [fresh] 값
 */
object EditedLinkMerger {

    fun merge(original: UrlData?, edited: UrlData, fresh: UrlData?): UrlData {
        if (original == null || fresh == null) {
            // 원래 행을 모르면 예전 동작: 사용자가 저장한 내용을 지킨다
            return if (edited.metaState != MetaState.DONE) edited.copy(metaState = MetaState.DONE) else edited
        }
        val titleChanged = edited.title != original.title
        val descriptionChanged = edited.description != original.description
        val imageChanged = edited.imgUrl != original.imgUrl
        val siteChanged = edited.siteName != original.siteName
        val anyChanged = titleChanged || descriptionChanged || imageChanged || siteChanged
        val state = when {
            !anyChanged -> fresh.metaState
            !MetaState.isPending(fresh.metaState) -> MetaState.DONE
            imageChanged -> MetaState.DONE
            else -> MetaState.PENDING_IMAGE
        }
        return edited.copy(
            id = fresh.id,
            title = if (titleChanged) edited.title else fresh.title,
            description = if (descriptionChanged) edited.description else fresh.description,
            imgUrl = if (imageChanged) edited.imgUrl else fresh.imgUrl,
            siteName = if (siteChanged) edited.siteName else fresh.siteName,
            importBatchId = fresh.importBatchId,
            metaState = state,
            // 수정 화면에서 바꾸지 않는 값: 그사이 목록에서 읽음 처리했을 수 있다
            isRead = fresh.isRead,
            // 다시 볼 링크 카드에 보인 시각 (42번 §3.2)도 수정 화면이 다루지 않는다
            revisitShownAt = fresh.revisitShownAt,
        )
    }
}
