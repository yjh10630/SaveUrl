package com.jinscompany.saveurl.domain.importer

import com.jinscompany.saveurl.domain.repository.ImportCommitResult
import com.jinscompany.saveurl.domain.repository.ImportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 가져오는 중 상태 */
sealed interface CommitState {
    data object Idle : CommitState
    data class Running(val done: Int, val total: Int) : CommitState
    data class Done(val result: ImportCommitResult) : CommitState
    /** 실패: 넣은 것은 모두 되돌렸다 */
    data class Failed(val error: ImportError) : CommitState
    /** 사용자가 취소: 넣은 것은 모두 되돌렸다 */
    data object Cancelled : CommitState
}

/**
 * 링크 넣기를 앱 범위에서 돌린다. 화면(ViewModel)이 사라져도(2분할에서 다른 행을 누름 등) 이어서 끝까지 넣고,
 * 취소·실패하면 이번 묶음을 모두 지워 가져오기 전 상태로 돌린다 (34번 §S7).
 * 프로세스가 죽으면 이미 넣은 묶음은 기록에 "일부만 가져옴"으로 남는다.
 */
@Singleton
class ImportCommitRunner @Inject constructor(
    private val repository: ImportRepository,
    private val metaFillScheduler: MetaFillScheduler,
    private val appScope: CoroutineScope,
) {
    private val _state = MutableStateFlow<CommitState>(CommitState.Idle)
    val state: StateFlow<CommitState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var currentBatchId: Long? = null

    /** 지금 넣고 있는 묶음 (기록 화면에서 "일부만 가져옴"과 구분) */
    val runningBatchId: Long? get() = currentBatchId.takeIf { _state.value is CommitState.Running }

    fun start(request: ImportCommitRequest) {
        if (job?.isActive == true) return
        _state.value = CommitState.Running(0, request.records.size)
        job = appScope.launch {
            var batchId: Long? = null
            try {
                val result = repository.commit(
                    request,
                    onBatchCreated = { batchId = it; currentBatchId = it },
                    onProgress = { done, total -> _state.value = CommitState.Running(done, total) },
                )
                _state.value = CommitState.Done(result)
                if (result.inserted > 0) metaFillScheduler.schedule()
            } catch (e: CancellationException) {
                batchId?.let { repository.rollback(it) }
                _state.value = CommitState.Cancelled
            } catch (e: ImportFailure) {
                batchId?.let { repository.rollback(it) }
                _state.value = CommitState.Failed(e.error)
            } catch (e: Exception) {
                batchId?.let { repository.rollback(it) }
                _state.value = CommitState.Failed(ImportError.IO)
            } finally {
                currentBatchId = null
            }
        }
    }

    /** 진행 중 취소 (확인 시트에서 [취소하기]). 끝나면 [CommitState.Cancelled] */
    fun cancel() {
        job?.cancel()
    }

    /** 결과를 화면이 받은 뒤 다음 가져오기를 위해 비운다 */
    fun reset() {
        if (job?.isActive == true) return
        _state.value = CommitState.Idle
    }
}
