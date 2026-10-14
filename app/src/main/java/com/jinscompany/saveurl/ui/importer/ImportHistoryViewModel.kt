package com.jinscompany.saveurl.ui.importer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.data.meta.WorkMetaFillScheduler
import com.jinscompany.saveurl.domain.importer.ImportCommitRunner
import com.jinscompany.saveurl.domain.model.ImportBatchSummary
import com.jinscompany.saveurl.domain.model.MetaFillProgress
import com.jinscompany.saveurl.domain.repository.ImportRepository
import com.jinscompany.saveurl.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 가져오기 기록 (34번 §S10): 묶음 되돌리기 + 미리보기 채우기 멈추기/모바일 데이터 */
@HiltViewModel
class ImportHistoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ImportRepository,
    private val preferences: PreferencesManager,
    private val scheduler: WorkMetaFillScheduler,
    private val runner: ImportCommitRunner,
) : ViewModel() {

    val history: StateFlow<List<ImportBatchSummary>?> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 기록 전체의 채우기 진행 (묶음별 합) */
    val fill: StateFlow<MetaFillProgress> = repository.observeHistory()
        .map { list -> MetaFillProgress(pending = list.sumOf { it.pendingCount }, total = list.filter { it.pendingCount > 0 }.sumOf { it.linkCount }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetaFillProgress(0, 0))

    val paused: StateFlow<Boolean> = preferences.metaFillPaused.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val cellular: StateFlow<Boolean> = preferences.metaFillCellular.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val toast = _toast.asSharedFlow()

    /** 지금 넣고 있는 묶음 ("일부만 가져옴"으로 보이지 않게) */
    fun runningBatchId(): Long? = runner.runningBatchId

    fun undo(batchId: Long) {
        viewModelScope.launch {
            val result = repository.undo(batchId)
            _toast.emit(context.getString(R.string.import_undo_done_toast, ImportViewModel.formatCount(result.deletedLinks)))
        }
    }

    fun setPaused(paused: Boolean) {
        viewModelScope.launch {
            preferences.setMetaFillPaused(paused)
            scheduler.reschedule()
        }
    }

    fun setCellular(allowed: Boolean) {
        viewModelScope.launch {
            preferences.setMetaFillCellular(allowed)
            scheduler.reschedule()
        }
    }
}
