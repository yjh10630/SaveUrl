package com.jinscompany.saveurl.domain.importer

/** 가져온 링크의 제목·썸네일 채우기 예약 (구현: WorkManager, data/meta) */
interface MetaFillScheduler {
    /** 채울 링크가 있으면 백그라운드 작업을 예약한다 (Wi-Fi 기본, 설정에 따라 모바일 데이터 허용) */
    fun schedule()

    /** 화면에 보이는(또는 연) 링크 하나를 바로 채운다 (모바일 데이터 허용, 동시 1개) */
    fun prioritize(linkId: Int)
}
