package com.jinscompany.saveurl.ui.importer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.data.importer.ImportSourceOpener
import com.jinscompany.saveurl.domain.importer.CategoryMode
import com.jinscompany.saveurl.domain.importer.CommitState
import com.jinscompany.saveurl.domain.importer.GroupRule
import com.jinscompany.saveurl.domain.importer.ImportCommitRunner
import com.jinscompany.saveurl.domain.importer.ImportError
import com.jinscompany.saveurl.domain.importer.ImportFailure
import com.jinscompany.saveurl.domain.importer.ImportFileReader
import com.jinscompany.saveurl.domain.importer.ImportPlanner
import com.jinscompany.saveurl.domain.importer.ImportPreview
import com.jinscompany.saveurl.domain.importer.ImportSelection
import com.jinscompany.saveurl.domain.importer.ParsedSource
import com.jinscompany.saveurl.domain.importer.ReadOutcome
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.repository.ImportRepository
import com.jinscompany.saveurl.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 링크 가져오기 (34번). 한 화면(라우트) 안에서 단계를 바꾸고, 결과는 이 ViewModel 이 들고 있어 회전·접기·펼치기에도 이어진다.
 * 넣기는 [ImportCommitRunner] 가 앱 범위에서 돌린다 (화면이 사라져도 끝까지 넣고, 취소하면 모두 되돌림).
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val opener: ImportSourceOpener,
    private val repository: ImportRepository,
    private val runner: ImportCommitRunner,
    private val inbox: ImportShareInbox,
    private val categoryRepository: CategoryRepository,
    private val lockSession: CategoryLockSession,
    private val preferences: PreferencesManager,
    private val mainListRequests: MainListRequests,
) : ViewModel() {

    val entry: ImportEntry = runCatching { ImportEntry.valueOf(savedStateHandle.get<String>(ARG_ENTRY) ?: "") }
        .getOrDefault(ImportEntry.SETTINGS)

    private val _step = MutableStateFlow<ImportStep>(ImportStep.Home)
    val step: StateFlow<ImportStep> = _step.asStateFlow()

    private val _effect = MutableSharedFlow<ImportEffect>(extraBufferCapacity = 4)
    val effect = _effect.asSharedFlow()

    val hasHistory: StateFlow<Boolean> = repository.observeHasHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isUnlocked: StateFlow<Boolean> = lockSession.isUnlocked

    /** 카테고리 선택 시트: 일반 카테고리 / 잠금 카테고리 (닫힌 세션에서는 보이지 않음 = 제안하지 않음) */
    data class CategoryNames(val visible: List<String>, val locked: List<String>)
    private val _categories = MutableStateFlow(CategoryNames(emptyList(), emptyList()))
    val categories: StateFlow<CategoryNames> = _categories.asStateFlow()

    /** 완료 화면 미리보기 카드: 이 묶음의 남은 개수 / 전체 */
    val doneFill: StateFlow<Pair<Int, Int>?> = combine(_step, repository.observeHistory()) { step, list ->
            val done = step as? ImportStep.Done ?: return@combine null
            list.firstOrNull { it.id == done.summary.batchId }?.let { it.pendingCount to it.linkCount }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var readJob: Job? = null
    private var lastPreview: ImportStep.Preview? = null
    private var lastOutcome: Pair<ImportFileInfo, ReadOutcome>? = null

    init {
        loadCategories()
        if (entry == ImportEntry.SHARE) {
            when (val share = inbox.take()) {
                is ImportShare.Files -> share.uris.firstOrNull()?.let { if (share.uris.size == 1) readUri(it) else readUris(share.uris) }
                is ImportShare.Text -> readSharedText(share.text)
                null -> Unit
            }
        }
        observeRunner()
        viewModelScope.launch {
            lockSession.isUnlocked.drop(1).collect { unlocked -> if (!unlocked) onRelocked() }
        }
    }

    /**
     * 다시 잠기면(앱을 1분 넘게 나갔다 옴) 잠금 카테고리 이름이 화면에 남지 않게 한다 (39번 독립 리뷰 R9, 37번 §1.3).
     * - 미리보기: 잠금 카테고리를 넣을 곳으로 골라 두었으면 미분류로 (폴더별 대상도 같음)
     * - 가져오는 중: 이름 대신 "링크를 넣고 있어요"
     * - 완료: 이름 대신 개수, [목록에서 보기]는 전체로
     */
    private suspend fun onRelocked() {
        val locked = runCatching { categoryRepository.get() }.getOrDefault(emptyList()).filter { it.isLocked }.map { it.name }.toSet()
        loadCategories()
        if (locked.isEmpty()) return
        _step.value = hideLockedNames(_step.value, locked)
        lastPreview = lastPreview?.let { hideLockedNames(it, locked) as ImportStep.Preview }
        pendingCommit = pendingCommit?.let { pending ->
            val label = pending.label
            pending.copy(
                preview = hideLockedNames(pending.preview, locked) as ImportStep.Preview,
                label = if (label is ProgressCategory.Single && label.name in locked) ProgressCategory.Hidden else label,
                relocked = true,
            )
        }
    }

    fun onHomeShown() {
        viewModelScope.launch { preferences.setImportEntrySeen() }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val all = runCatching { categoryRepository.get() }.getOrDefault(emptyList())
            _categories.value = CategoryNames(
                visible = all.filter { !it.isLocked && it.isEditable && it.name != CategoryModel.UNCATEGORIZED }.map { it.name },
                locked = all.filter { it.isLocked }.map { it.name },
            )
        }
    }

    /** 모든 카테고리 이름 (잠금 포함): "기존 '{이름}'에 합쳐요" 판단. 잠금 여부는 화면에 보이지 않는다 */
    fun existingCategoryNames(): Set<String> = (_categories.value.visible + _categories.value.locked).toSet() + CategoryModel.UNCATEGORIZED

    // ---- 읽기 ----

    fun onFilePicked(uri: Uri) = readUri(uri)

    private fun readUri(uri: Uri) {
        val (name, size) = opener.describe(uri)
        val file = ImportFileInfo(name, size)
        startReading(file) { reader, onFound, isCancelled ->
            val stream = opener.open(uri) ?: throw ImportFailure(ImportError.IO)
            stream.use { reader.read(name, size, it, isCancelled, onFound) }
        }
    }

    /** 공유로 파일 여러 개: 파일마다 읽어 합친다 → "어떤 파일에서 가져올까요?" */
    private fun readUris(uris: List<Uri>) {
        val described = uris.map { it to opener.describe(it) }
        val file = ImportFileInfo(context.getString(R.string.import_multi_files, uris.size), described.sumOf { it.second.second ?: 0L })
        startReading(file) { reader, onFound, isCancelled ->
            val sources = ArrayList<ParsedSource>()
            var truncated = false
            val errors = ArrayList<ImportError>()
            for ((uri, info) in described) {
                try {
                    val outcome = opener.open(uri)?.use { reader.read(info.first, info.second, it, isCancelled, onFound) }
                    if (outcome == null) errors.add(ImportError.IO)
                    else { sources.addAll(outcome.sources); truncated = truncated || outcome.truncated }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ImportFailure) {
                    errors.add(e.error)
                } catch (e: Exception) {
                    errors.add(ImportError.IO)
                }
            }
            // 모두 못 읽었으면 실제 원인을 보여 준다 (너무 큼·지원 안 함 등). "링크 없음"은 다른 원인이 없을 때만 (39번 독립 리뷰 R13)
            if (sources.isEmpty()) throw ImportFailure(errors.firstOrNull { it != ImportError.NO_LINKS } ?: ImportError.NO_LINKS)
            // 링크가 없던 파일은 "못 읽음"으로 세지 않는다
            val unread = errors.count { it != ImportError.NO_LINKS && it != ImportError.EMPTY }
            ReadOutcome(file.name, file.size, isZip = true, sources = sources, truncated = truncated, unreadFiles = unread)
        }
    }

    private fun readSharedText(text: String) {
        val label = context.getString(R.string.import_label_shared_text)
        val file = ImportFileInfo(label, null)
        startReading(file) { reader, _, _ ->
            val source = reader.readText(text, label)
            if (source.candidates.isEmpty()) throw ImportFailure(ImportError.NO_LINKS)
            ReadOutcome(label, null, isZip = false, sources = listOf(source), truncated = false)
        }
    }

    private fun startReading(
        file: ImportFileInfo,
        block: suspend (ImportFileReader, (Int) -> Unit, () -> Boolean) -> ReadOutcome,
    ) {
        readJob?.cancel()
        _step.value = ImportStep.Reading(file, found = 0, visible = false)
        readJob = viewModelScope.launch {
            // 0.3초 안에 끝나면 읽는 중 화면 없이 바로 다음 화면 (깜빡임 방지)
            val reveal = launch {
                delay(READING_REVEAL_DELAY_MS)
                _step.update { (it as? ImportStep.Reading)?.copy(visible = true) ?: it }
            }
            try {
                val job = coroutineContext[Job]
                val outcome = withContext(Dispatchers.IO) {
                    var lastReport = 0L
                    block(
                        ImportFileReader(),
                        { found ->
                            val now = System.currentTimeMillis()
                            if (now - lastReport >= FOUND_REPORT_INTERVAL_MS) {
                                lastReport = now
                                _step.update { (it as? ImportStep.Reading)?.copy(found = found) ?: it }
                            }
                        },
                        { job?.isActive == false },
                    )
                }
                reveal.cancel()
                lastOutcome = file to outcome
                if (outcome.sources.size >= 2) {
                    _step.value = ImportStep.PickSources(file, outcome, defaultSourceSelection(outcome))
                } else {
                    showPreview(file, outcome.sources, outcome.truncated, outcome.unreadFiles)
                }
            } catch (e: ImportFailure) {
                reveal.cancel()
                _step.value = ImportStep.Error(e.error, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                // 읽기 상한 안에서도 기기 메모리가 모자라면 앱을 끝내지 않고 "파일이 너무 커요" (39번 독립 리뷰 R4)
                reveal.cancel()
                _step.value = ImportStep.Error(ImportError.TOO_BIG, file)
            } catch (e: Exception) {
                reveal.cancel()
                _step.value = ImportStep.Error(ImportError.IO, file)
            }
        }
    }

    /** 읽는 중 [취소] / 뒤로: 홈으로 (공유로 들어왔으면 닫기) */
    fun cancelReading() {
        readJob?.cancel()
        readJob = null
        if (entry == ImportEntry.SHARE) _effect.tryEmit(ImportEffect.Close) else _step.value = ImportStep.Home
    }

    // ---- 대화 고르기 ----

    /** 기본: 링크가 가장 많은 1개 (34번 Q16) */
    private fun defaultSourceSelection(outcome: ReadOutcome): Set<Int> =
        setOf(outcome.sources.indices.maxByOrNull { outcome.sources[it].candidates.size } ?: 0)

    fun toggleSource(index: Int) = _step.update { s ->
        if (s !is ImportStep.PickSources) s
        else s.copy(selected = if (index in s.selected) s.selected - index else s.selected + index)
    }

    fun setAllSources(selectAll: Boolean) = _step.update { s ->
        if (s !is ImportStep.PickSources) s
        else s.copy(selected = if (selectAll) s.outcome.sources.indices.toSet() else emptySet())
    }

    fun confirmSources() {
        val s = _step.value as? ImportStep.PickSources ?: return
        if (s.selected.isEmpty()) return
        val sources = s.selected.sorted().map { s.outcome.sources[it] }
        viewModelScope.launch { showPreview(s.file, sources, s.outcome.truncated, s.outcome.unreadFiles) }
    }

    private suspend fun showPreview(file: ImportFileInfo, sources: List<ParsedSource>, truncated: Boolean, unreadFiles: Int = 0) {
        val existing = withContext(Dispatchers.IO) { repository.existingNormalizedKeys() }
        val label = sources.singleOrNull()?.let { s -> s.roomName?.takeIf { it.isNotBlank() } ?: s.fileName } ?: file.name
        val preview = withContext(Dispatchers.Default) { ImportPlanner.buildPreview(sources, existing, label, truncated, unreadFiles) }
        val step = ImportStep.Preview(file, preview, ImportPlanner.defaultSelection(preview), query = "")
        lastPreview = step
        _step.value = step
    }

    // ---- 미리보기 ----

    private fun updatePreview(block: (ImportStep.Preview) -> ImportStep.Preview) {
        _step.update { s -> if (s is ImportStep.Preview) block(s).also { lastPreview = it } else s }
    }

    private fun updateSelection(block: (ImportSelection) -> ImportSelection) = updatePreview { it.copy(selection = block(it.selection)) }

    fun toggleSender(name: String) = updateSelection { sel ->
        sel.copy(selectedSenders = if (name in sel.selectedSenders) sel.selectedSenders - name else sel.selectedSenders + name)
    }

    fun setAllSenders(selectAll: Boolean) = updatePreview { p ->
        p.copy(selection = p.selection.copy(selectedSenders = if (selectAll) p.preview.senders.map { it.first }.toSet() else emptySet()))
    }

    fun setCategoryMode(mode: CategoryMode) = updateSelection { it.copy(categoryMode = mode) }

    fun setGroupRule(key: String, rule: GroupRule) = updateSelection { it.copy(groupRules = it.groupRules + (key to rule)) }

    fun setUseOriginalDate(original: Boolean) = updateSelection { it.copy(useOriginalDate = original) }

    fun toggleItem(key: String) = updateSelection { sel ->
        sel.copy(unchecked = if (key in sel.unchecked) sel.unchecked - key else sel.unchecked + key)
    }

    fun setQuery(query: String) = updatePreview { it.copy(query = query) }

    /** "모두 선택 / 모두 해제": 검색 중이면 보이는 행만 */
    fun setAllItems(select: Boolean) = updatePreview { p ->
        val keys = visibleRows(p).filter { !it.exists }.map { it.key }.toSet()
        val unchecked = if (select) p.selection.unchecked - keys else p.selection.unchecked + keys
        p.copy(selection = p.selection.copy(unchecked = unchecked))
    }

    // ---- 넣기 ----

    fun commit() {
        val p = _step.value as? ImportStep.Preview ?: return
        val request = ImportPlanner.buildRequest(p.preview, p.selection, now = System.currentTimeMillis())
        if (request.records.isEmpty()) return
        // 앞선 가져오기가 아직 넣는 중이면(화면을 나갔다 다시 들어옴) 기다리게 한다
        if (runner.state.value is CommitState.Running) {
            _effect.tryEmit(ImportEffect.Toast(context.getString(R.string.import_busy)))
            return
        }
        val names = request.records.map { it.category }.distinct()
        val label = when {
            names.size > 1 -> ProgressCategory.Many(names.size)
            names.firstOrNull() == null -> ProgressCategory.Uncategorized
            else -> ProgressCategory.Single(names.first()!!)
        }
        pendingCommit = PendingCommit(p, request.records.size, label)
        runner.reset()
        runner.start(request)
    }

    private data class PendingCommit(
        val preview: ImportStep.Preview,
        val total: Int,
        val label: ProgressCategory,
        /** 넣는 동안 다시 잠겼음: 완료 화면에서도 잠금 카테고리 이름을 숨긴다 */
        val relocked: Boolean = false,
    )
    private var pendingCommit: PendingCommit? = null

    private fun observeRunner() {
        viewModelScope.launch {
            var revealJob: Job? = null
            runner.state.collect { state ->
                val pending = pendingCommit ?: return@collect
                when (state) {
                    is CommitState.Running -> {
                        val current = _step.value as? ImportStep.Progress
                        _step.value = ImportStep.Progress(state.done, state.total, pending.label, visible = current?.visible ?: false)
                        if (revealJob == null) {
                            // 0.5초 안에 끝나면 진행 화면 없이 바로 완료 (34번 Q13)
                            revealJob = launch {
                                delay(PROGRESS_REVEAL_DELAY_MS)
                                _step.update { (it as? ImportStep.Progress)?.copy(visible = true) ?: it }
                            }
                        }
                    }
                    is CommitState.Done -> {
                        revealJob?.cancel(); revealJob = null
                        _step.value = ImportStep.Done(buildSummary(pending, state))
                        pendingCommit = null
                        runner.reset()
                    }
                    is CommitState.Failed -> {
                        revealJob?.cancel(); revealJob = null
                        _step.value = ImportStep.Error(state.error, pending.preview.file, backToPreview = true)
                        pendingCommit = null
                        runner.reset()
                    }
                    CommitState.Cancelled -> {
                        revealJob?.cancel(); revealJob = null
                        _step.value = pending.preview
                        pendingCommit = null
                        runner.reset()
                    }
                    CommitState.Idle -> Unit
                }
            }
        }
    }

    private suspend fun buildSummary(pending: PendingCommit, done: CommitState.Done): ImportDoneSummary {
        val p = pending.preview
        val result = done.result
        val used = ImportPlanner.targetCategories(p.preview, p.selection)
        val lockedNow = runCatching { categoryRepository.get() }.getOrDefault(emptyList()).filter { it.isLocked }.map { it.name }.toSet()
        val hideLocked = !lockSession.isUnlocked.value || pending.relocked
        val usedHidden = hideLocked && used.any { it in lockedNow }
        val includesUncategorized = ImportPlanner.selectedItems(p.preview, p.selection).any { ImportPlanner.targetCategory(it, p.selection) == null }
        val category = when {
            used.isEmpty() -> DoneCategory.Uncategorized
            used.size == 1 && !includesUncategorized && !usedHidden -> DoneCategory.Single(used.first(), used.first() in result.createdCategories)
            else -> DoneCategory.Many(used.size + if (includesUncategorized) 1 else 0, result.createdCategories.count { it != CategoryModel.UNCATEGORIZED })
        }
        val selected = ImportPlanner.selectedItems(p.preview, p.selection)
        loadCategories()
        return ImportDoneSummary(
            batchId = result.batchId,
            inserted = result.inserted,
            skipped = p.preview.counts.existing + result.skippedAtCommit,
            label = p.preview.label,
            createdAt = System.currentTimeMillis(),
            category = category,
            useOriginalDate = p.selection.useOriginalDate && p.preview.hasDates,
            firstDate = selected.mapNotNull { it.date }.minOrNull(),
            lastDate = selected.mapNotNull { it.date }.maxOrNull(),
            dateMiss = if (p.selection.useOriginalDate) ImportPlanner.dateMissCount(p.preview, p.selection) else 0,
            viewCategory = (category as? DoneCategory.Single)?.name,
            createdCategories = result.createdCategories.filter { it != CategoryModel.UNCATEGORIZED },
            hideCreatedNames = hideLocked && result.createdCategories.any { it in lockedNow },
            needsFill = selected.any { it.candidate.title.isNullOrBlank() || it.candidate.imageUrl.isNullOrBlank() },
        )
    }

    /** 가져오는 중 취소 (확인 시트에서 [취소하기]) */
    fun cancelCommit() = runner.cancel()

    /** 저장 공간 부족 오류 → 미리보기로 */
    fun backToPreview() {
        lastPreview?.let { _step.value = it }
    }

    /** 오류 화면 "가져오는 방법 보기" */
    fun backToHome() {
        readJob?.cancel()
        _step.value = ImportStep.Home
    }

    // ---- 완료 ----

    fun undoDone() {
        val done = _step.value as? ImportStep.Done ?: return
        viewModelScope.launch {
            val result = repository.undo(done.summary.batchId)
            _effect.emit(ImportEffect.Toast(context.getString(R.string.import_undo_done_toast, formatCount(result.deletedLinks))))
            _effect.emit(ImportEffect.Close)
        }
    }

    fun viewInList() {
        val done = _step.value as? ImportStep.Done ?: return
        mainListRequests.showCategory(done.summary.viewCategory)
        _effect.tryEmit(ImportEffect.ViewInList)
    }

    companion object {
        const val ARG_ENTRY = "entry"
        private const val READING_REVEAL_DELAY_MS = 300L
        private const val PROGRESS_REVEAL_DELAY_MS = 500L
        private const val FOUND_REPORT_INTERVAL_MS = 200L

        /** 목록에 보이는 행 (보낸 사람·폴더 제외는 숨김, 검색어) */
        fun visibleRows(p: ImportStep.Preview) = p.preview.items.filter { item ->
            ImportPlanner.isShown(item, p.preview, p.selection) &&
                (p.query.isBlank() || item.domain.contains(p.query, ignoreCase = true) || item.url.contains(p.query, ignoreCase = true) ||
                    item.title?.contains(p.query, ignoreCase = true) == true)
        }

        fun formatCount(n: Int): String = java.text.NumberFormat.getInstance(java.util.Locale.KOREA).format(n)

        /** [onRelocked] 의 단계별 처리 (JVM 테스트) */
        internal fun hideLockedNames(step: ImportStep, locked: Set<String>): ImportStep = when (step) {
            is ImportStep.Preview -> {
                val sel = step.selection
                val mode = (sel.categoryMode as? CategoryMode.Single)
                    ?.takeIf { ImportPlanner.normalizeName(it.name) in locked }
                    ?.let { CategoryMode.Uncategorized }
                    ?: sel.categoryMode
                val rules = sel.groupRules.mapValues { (_, rule) ->
                    if (ImportPlanner.normalizeName(rule.targetName) in locked) rule.copy(targetName = null) else rule
                }
                step.copy(selection = sel.copy(categoryMode = mode, groupRules = rules))
            }
            is ImportStep.Progress -> {
                val label = step.categoryLabel
                if (label is ProgressCategory.Single && label.name in locked) step.copy(categoryLabel = ProgressCategory.Hidden) else step
            }
            is ImportStep.Done -> {
                val summary = step.summary
                val category = summary.category
                val hideSingle = category is DoneCategory.Single && category.name in locked
                step.copy(
                    summary = summary.copy(
                        category = if (hideSingle) DoneCategory.Many(1, if ((category as DoneCategory.Single).isNew) 1 else 0) else category,
                        viewCategory = summary.viewCategory?.takeUnless { it in locked },
                        hideCreatedNames = summary.hideCreatedNames || summary.createdCategories.any { it in locked },
                    )
                )
            }
            else -> step
        }
    }
}
