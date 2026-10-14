package com.jinscompany.saveurl.data.source

import androidx.room.withTransaction
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.room.CategoryDao
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.ui.FilterDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CategoryDBSourceImpl @Inject constructor (
    private val categoryDao: CategoryDao,
    private val db: AppDatabase
) : CategoryDBSource {
    override suspend fun getAll(): List<CategoryModel> = withContext(Dispatchers.IO) {
        // contentCnt 에는 저장값이 아닌 BaseSaveUrl 기준 실제 링크 수가 들어감
        return@withContext categoryDao.getAllWithLinkCount()
    }

    override suspend fun insert(data: CategoryModel): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            db.withTransaction {
                // 같은 이름의 카테고리가 이미 있으면(편집 화면에 보이지 않는 "미분류" 포함) 중복 생성하지 않음
                if (isReservedName(data.name) || categoryDao.get(data.name) != null) return@withTransaction false
                val orderMaxCnt = categoryDao.getMaxOrder() ?: 0
                data.order = orderMaxCnt + 1
                val id = categoryDao.insert(data)
                -1 < id
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun delete(data: CategoryModel): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            // 링크 이동 / 휴지통 재매핑 / 자동 분류 규칙 정리 / 카테고리 삭제 / 순서 재정렬을 하나의 트랜잭션으로 처리
            db.withTransaction {
                val target = categoryDao.get(data.name) ?: return@withTransaction false
                // "미분류" 등 편집 불가 카테고리는 삭제하지 않음
                if (!target.isEditable || target.name == CategoryModel.UNCATEGORIZED) return@withTransaction false

                // 링크는 카테고리를 이름으로 참조 → 삭제된 카테고리의 링크/휴지통 항목을 "미분류" 로 이동
                val movedLinks = db.baseSaveUrlDao().renameCategory(target.name, CategoryModel.UNCATEGORIZED)
                val movedTrash = db.trashDao().renameCategory(target.name, CategoryModel.UNCATEGORIZED)
                // 이 카테고리를 가리키는 자동 분류 규칙(거절 기록 포함)도 지운다 (36번 §8)
                db.autoCategoryRuleDao().deleteByCategoryId(target.id)
                if (movedLinks > 0 || movedTrash > 0) ensureUncategorizedCategory()

                categoryDao.delete(target)
                val itemsToUpdate = categoryDao.getCategoriesAfter(target.order)
                itemsToUpdate.forEach {
                    categoryDao.update(it.copy(order = it.order - 1))
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** "미분류" 카테고리가 없으면 편집 불가로 생성, 사용자가 같은 이름으로 만든 것이 있으면 편집 불가로 전환 */
    private suspend fun ensureUncategorizedCategory() {
        val existing = categoryDao.get(CategoryModel.UNCATEGORIZED)
        if (existing == null) {
            val orderMaxCnt = categoryDao.getMaxOrder() ?: 0
            categoryDao.insert(
                CategoryModel(
                    name = CategoryModel.UNCATEGORIZED,
                    order = orderMaxCnt + 1,
                    isEditable = false,
                )
            )
        } else if (existing.isEditable) {
            categoryDao.update(existing.copy(isEditable = false))
        }
    }

    /**
     * 가상 카테고리 이름("전체"=선택 안 함, "북마크")과 기본 카테고리 "미분류"는 새로 만들거나 그 이름으로 바꿀 수 없다.
     * 규칙이 가리키는 카테고리를 "전체"로 바꾸면 규칙이 "선택 안 함"을 자동으로 고르게 된다 (40번 독립 리뷰 R15)
     */
    private fun isReservedName(name: String): Boolean =
        name.isBlank() || name == FilterDefaults.CATEGORY_ALL || name == FilterDefaults.CATEGORY_BOOKMARK || name == CategoryModel.UNCATEGORIZED

    override suspend fun update(oldName: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            db.withTransaction {
                val updateData = categoryDao.get(oldName)
                // 편집 불가 카테고리("미분류")는 이름 변경 불가, 이미 있는 이름으로 변경하면 두 카테고리가 섞이므로 거부
                if (updateData != null && updateData.isEditable && !isReservedName(newName) && categoryDao.get(newName) == null) {
                    updateData.name = newName
                    categoryDao.update(updateData)
                    // 링크는 카테고리를 이름으로 참조하므로 함께 변경하지 않으면 변경된 카테고리로 필터링 시 기존 링크가 보이지 않음.
                    // 자동 분류 규칙은 id 로 가리키므로 바꿀 것이 없다
                    db.baseSaveUrlDao().renameCategory(oldName, newName)
                    db.trashDao().renameCategory(oldName, newName)
                    true
                } else false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun observeLockedNames(): Flow<List<String>> = categoryDao.observeLockedNames()

    override suspend fun isLocked(name: String): Boolean = withContext(Dispatchers.IO) {
        categoryDao.countLocked(name) > 0
    }

    override suspend fun setLocked(name: String, locked: Boolean): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            db.withTransaction {
                val target = categoryDao.get(name) ?: return@withTransaction false
                // "미분류" 는 편집 화면에 나오지 않고 삭제된 카테고리의 링크가 모이는 곳이라 잠그지 않는다
                if (!target.isEditable || target.name == CategoryModel.UNCATEGORIZED) return@withTransaction false
                // 이 카테고리를 가리키는 자동 분류 규칙은 남겨 두되, 잠겨 있는 동안은 적용하지 않는다 (AutoCategoryRuleDao.getApplicable)
                categoryDao.setLocked(name, locked)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun lockOrCreate(names: Collection<String>) = withContext(Dispatchers.IO) {
        db.withTransaction {
            names.filter { it.isNotBlank() && it != CategoryModel.UNCATEGORIZED }.distinct().forEach { name ->
                val existing = categoryDao.get(name)
                if (existing == null) {
                    val orderMaxCnt = categoryDao.getMaxOrder() ?: 0
                    categoryDao.insert(CategoryModel(name = name, order = orderMaxCnt + 1, isLocked = true))
                } else if (existing.isEditable && !existing.isLocked) {
                    categoryDao.setLocked(name, true)
                }
            }
        }
    }
}
