package com.jinscompany.saveurl.data.source

import androidx.room.withTransaction
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.room.CategoryDao
import com.jinscompany.saveurl.domain.model.CategoryModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CategoryDBSourceImpl @Inject constructor (
    private val categoryDao: CategoryDao,
    private val db: AppDatabase
) : CategoryDBSource {
    override suspend fun getAll(): List<CategoryModel> = withContext(Dispatchers.IO) {
        return@withContext categoryDao.getAll()
    }

    override suspend fun insert(data: CategoryModel): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            db.withTransaction {
                // 같은 이름의 카테고리가 이미 있으면(편집 화면에 보이지 않는 "미분류" 포함) 중복 생성하지 않음
                if (categoryDao.get(data.name) != null) return@withTransaction false
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
            // 링크 이동 / 휴지통 재매핑 / 학습값 정리 / 카테고리 삭제 / 순서 재정렬을 하나의 트랜잭션으로 처리
            db.withTransaction {
                val target = categoryDao.get(data.name) ?: return@withTransaction false
                // "미분류" 등 편집 불가 카테고리는 삭제하지 않음
                if (!target.isEditable || target.name == CategoryModel.UNCATEGORIZED) return@withTransaction false

                // 링크는 카테고리를 이름으로 참조 → 삭제된 카테고리의 링크/휴지통 항목을 "미분류" 로 이동
                val movedLinks = db.baseSaveUrlDao().renameCategory(target.name, CategoryModel.UNCATEGORIZED)
                val movedTrash = db.trashDao().renameCategory(target.name, CategoryModel.UNCATEGORIZED)
                // 삭제된 카테고리를 계속 추천하고 저장 시 다시 만들지 않도록 도메인 학습값 제거
                db.domainCategoryDao().deleteByCategory(target.name)
                if (movedLinks > 0 || movedTrash > 0) ensureUncategorizedCategory(movedLinks)

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
    private suspend fun ensureUncategorizedCategory(addedCount: Int) {
        val existing = categoryDao.get(CategoryModel.UNCATEGORIZED)
        if (existing == null) {
            val orderMaxCnt = categoryDao.getMaxOrder() ?: 0
            categoryDao.insert(
                CategoryModel(
                    name = CategoryModel.UNCATEGORIZED,
                    contentCnt = addedCount,
                    order = orderMaxCnt + 1,
                    isEditable = false,
                )
            )
        } else {
            categoryDao.update(
                existing.copy(contentCnt = existing.contentCnt + addedCount, isEditable = false)
            )
        }
    }

    override suspend fun update(oldName: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            db.withTransaction {
                val updateData = categoryDao.get(oldName)
                // 편집 불가 카테고리("미분류")는 이름 변경 불가, 이미 있는 이름으로 변경하면 두 카테고리가 섞이므로 거부
                if (updateData != null && updateData.isEditable && categoryDao.get(newName) == null) {
                    updateData.name = newName
                    categoryDao.update(updateData)
                    // 링크는 카테고리를 이름으로 참조하므로 함께 변경하지 않으면
                    // 변경된 카테고리로 필터링 시 기존 링크가 보이지 않고, 도메인 학습값이 옛 이름을 추천함
                    db.baseSaveUrlDao().renameCategory(oldName, newName)
                    db.trashDao().renameCategory(oldName, newName)
                    db.domainCategoryDao().renameCategory(oldName, newName)
                    true
                } else false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}