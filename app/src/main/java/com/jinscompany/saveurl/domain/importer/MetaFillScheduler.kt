package com.jinscompany.saveurl.domain.importer

/** 가져온 링크의 제목·썸네일 채우기 예약 (구현: WorkManager, data/meta) */
interface MetaFillScheduler {
    /** 채울 링크가 있으면 백그라운드 작업을 예약한다 (Wi-Fi 기본, 설정에 따라 모바일 데이터 허용) */
    fun schedule()

    /**
     * 화면에 보이는(또는 연) 링크 하나를 바로 채운다 (모바일 데이터 허용, 동시 1개, 최근 몇 개만 기다림).
     * [explicit] = 사용자가 링크를 직접 열었음 (채우기를 멈춰 두었어도 채우고, 화면에서 사라져도 취소하지 않음)
     */
    fun prioritize(linkId: Int, explicit: Boolean = false)

    /** 카드가 화면에서 사라짐: 기다리던 요청을 빼고, 채우는 중이면 멈춘다 */
    fun cancelPriority(linkId: Int)
}
