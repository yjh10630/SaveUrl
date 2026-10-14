package com.jinscompany.saveurl.ui.save_screen

import com.jinscompany.saveurl.domain.autocat.AutoCategorySaveFlow
import com.jinscompany.saveurl.domain.repository.AutoCategoryRepository
import io.mockk.coEvery
import io.mockk.mockk

/** 자동 분류가 꺼져 있는 저장 화면 (자동 분류와 관계없는 테스트용) */
internal fun disabledAutoCategory(): AutoCategorySaveFlow = mockk(relaxed = true) {
    coEvery { isEnabled() } returns false
    coEvery { this@mockk.match(any()) } returns null
}

/** 규칙이 하나도 없는 저장소 */
internal fun emptyAutoCategoryRepository(): AutoCategoryRepository = mockk(relaxed = true) {
    coEvery { findByKey(any()) } returns null
    coEvery { getApplicableRules() } returns emptyList()
}
