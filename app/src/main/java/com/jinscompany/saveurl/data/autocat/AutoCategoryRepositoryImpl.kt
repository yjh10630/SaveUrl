package com.jinscompany.saveurl.data.autocat

import androidx.room.withTransaction
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.room.AutoCategoryRuleDao
import com.jinscompany.saveurl.domain.autocat.AutoCategoryKeys
import com.jinscompany.saveurl.domain.autocat.AutoCategoryMatcher
import com.jinscompany.saveurl.domain.autocat.AutoRuleStats
import com.jinscompany.saveurl.domain.autocat.RuleKey
import com.jinscompany.saveurl.domain.autocat.RuleSuggester
import com.jinscompany.saveurl.domain.autocat.RuleSuggestion
import com.jinscompany.saveurl.domain.autocat.SuggestThresholds
import com.jinscompany.saveurl.domain.model.AutoCatLinkRow
import com.jinscompany.saveurl.domain.model.AutoCategoryRule
import com.jinscompany.saveurl.domain.model.AutoRuleSource
import com.jinscompany.saveurl.domain.model.AutoRuleState
import com.jinscompany.saveurl.domain.model.AutoRuleWithCategory
import com.jinscompany.saveurl.domain.repository.AutoCategoryRepository
import com.jinscompany.saveurl.domain.repository.BulkMoveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AutoCategoryRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val dao: AutoCategoryRuleDao,
) : AutoCategoryRepository {

    private val clock: () -> Long = { System.currentTimeMillis() }

    override fun observeRules(): Flow<List<AutoRuleWithCategory>> = dao.observeAllWithCategory()

    override suspend fun getRules(): List<AutoRuleWithCategory> = withContext(Dispatchers.IO) { dao.getAllWithCategory() }

    override suspend fun getRule(id: Long): AutoRuleWithCategory? = withContext(Dispatchers.IO) { dao.getWithCategory(id) }

    override suspend fun getApplicableRules(): List<AutoRuleWithCategory> = withContext(Dispatchers.IO) { dao.getApplicable() }

    override suspend fun findByKey(key: RuleKey): AutoCategoryRule? = withContext(Dispatchers.IO) {
        dao.getByKey(key.type.name, key.pattern)
    }

    override suspend fun createOrActivate(key: RuleKey, categoryId: Int, source: AutoRuleSource): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val existing = dao.getByKey(key.type.name, key.pattern)
            if (existing != null) {
                dao.update(
                    AutoRuleStats.resetRecent(existing).copy(
                        categoryId = categoryId,
                        state = AutoRuleState.ACTIVE.name,
                        source = if (existing.ruleState == AutoRuleState.DISMISSED) source.name else existing.source,
                    )
                )
                existing.id
            } else {
                dao.insert(
                    AutoCategoryRule(
                        matchType = key.type.name,
                        pattern = key.pattern,
                        categoryId = categoryId,
                        source = source.name,
                        createdAt = clock(),
                    )
                )
            }
        }
    }

    override suspend fun setCategory(ruleId: Long, categoryId: Int) = update(ruleId) {
        AutoRuleStats.resetRecent(it).copy(categoryId = categoryId)
    }

    override suspend fun setState(ruleId: Long, state: AutoRuleState) = update(ruleId) {
        AutoRuleStats.resetRecent(it).copy(state = state.name)
    }

    override suspend fun resetRecent(ruleId: Long) = update(ruleId) { AutoRuleStats.resetRecent(it) }

    override suspend fun delete(ruleId: Long) {
        withContext(Dispatchers.IO) { dao.delete(ruleId) }
    }

    override suspend fun dismissSuggestion(key: RuleKey, categoryId: Int) {
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val existing = dao.getByKey(key.type.name, key.pattern)
                when {
                    existing == null -> dao.insert(
                        AutoCategoryRule(
                            matchType = key.type.name, pattern = key.pattern, categoryId = categoryId,
                            state = AutoRuleState.DISMISSED.name, source = AutoRuleSource.SUGGESTION.name, createdAt = clock(),
                        )
                    )
                    // 켜짐·꺼짐 규칙은 거절로 덮지 않는다 (제안은 규칙이 없는 키에만 나오므로 경합일 때만)
                    existing.ruleState == AutoRuleState.DISMISSED -> dao.update(existing.copy(categoryId = categoryId))
                    else -> Unit
                }
            }
        }
    }

    override suspend fun recordApplication(ruleId: Long, savedCategoryId: Int): AutoCategoryRule? = withContext(Dispatchers.IO) {
        db.withTransaction {
            val rule = dao.get(ruleId) ?: return@withTransaction null
            val recorded = AutoRuleStats.record(rule, savedCategoryId, clock())
            dao.update(recorded)
            recorded
        }
    }

    override suspend fun markFixPromptShown(ruleId: Long) = update(ruleId) { it.copy(fixPromptShown = true) }

    override suspend fun suggestions(thresholds: SuggestThresholds): List<RuleSuggestion> = withContext(Dispatchers.IO) {
        val evidence = dao.getEvidence()
        val rules = dao.getAll()
        val categories = db.categoryDao().getAll().filter { !it.isLocked }.associate { it.name to it.id }
        RuleSuggester.suggest(evidence, rules, categories, thresholds)
    }

    override suspend fun linksForKey(key: RuleKey): List<AutoCatLinkRow> = withContext(Dispatchers.IO) {
        // 저장할 때 이 규칙이 고를 링크만: 더 구체적인 켜진 규칙(잠금 카테고리 규칙 포함)이 이기는 링크는 빼고 (40번 독립 리뷰 R3).
        // 만들기 전이면 이 키를 임시로 넣어 판정한다
        val ruleKeys = dao.getAll().filter { it.ruleState == AutoRuleState.ACTIVE }.mapTo(HashSet()) { it.key }.apply { add(key) }
        dao.getVisibleLinkRows().filter { row ->
            val keys = AutoCategoryKeys.keysOf(row.url) ?: return@filter false
            AutoCategoryMatcher.winningKey(keys, ruleKeys) == key
        }
    }

    override suspend fun moveUnfiled(ids: List<Int>, categoryName: String): BulkMoveResult = withContext(Dispatchers.IO) {
        db.withTransaction {
            val previous = LinkedHashMap<Int, String?>()
            ids.distinct().chunked(CHUNK).forEach { chunk ->
                val unfiled = dao.getUnfiled(chunk)
                if (unfiled.isEmpty()) return@forEach
                dao.moveUnfiled(unfiled.map { it.id }, categoryName)
                unfiled.forEach { previous[it.id] = it.category }
            }
            BulkMoveResult(categoryName, previous)
        }
    }

    override suspend fun undoMove(result: BulkMoveResult) {
        withContext(Dispatchers.IO) {
            db.withTransaction {
                result.previous.entries.groupBy({ it.value }, { it.key }).forEach { (previous, ids) ->
                    ids.chunked(CHUNK).forEach { dao.restoreCategory(it, previous, result.categoryName) }
                }
            }
        }
    }

    override suspend fun countRulesForCategory(categoryId: Int): Int = withContext(Dispatchers.IO) {
        dao.countRulesForCategory(categoryId)
    }

    private suspend fun update(ruleId: Long, change: (AutoCategoryRule) -> AutoCategoryRule) {
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val rule = dao.get(ruleId) ?: return@withTransaction
                dao.update(change(rule))
            }
        }
    }

    private companion object {
        /** SQLite IN 변수 상한을 넘지 않도록 */
        const val CHUNK = 500
    }
}
