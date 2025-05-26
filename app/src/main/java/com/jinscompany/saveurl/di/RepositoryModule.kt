package com.jinscompany.saveurl.di

import android.content.Context
import com.jinscompany.saveurl.data.CategoryRepositoryImpl
import com.jinscompany.saveurl.data.TrashRepositoryImpl
import com.jinscompany.saveurl.data.UrlRepositoryImpl
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.room.TrashDao
import com.jinscompany.saveurl.data.source.CategoryDBSource
import com.jinscompany.saveurl.data.source.LocalUrlDBSource
import com.jinscompany.saveurl.data.source.UrlParserSource
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.repository.TrashRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.utils.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class RepositoryModule {

    @Provides
    @Singleton
    fun provideUrlRepository(
        localUrlDBSource: LocalUrlDBSource,
        urlParserSource: UrlParserSource,
        db: AppDatabase,
    ): UrlRepository = UrlRepositoryImpl(localUrlDBSource, urlParserSource, db)

    @Provides
    @Singleton
    fun provideCategoryRepository(categoryDBSource: CategoryDBSource): CategoryRepository =
        CategoryRepositoryImpl(categoryDBSource)

    @Provides
    @Singleton
    fun provideAppSettingRepository(
        trashDao: TrashDao,
        db: AppDatabase,
        preferencesManager: PreferencesManager
    ): TrashRepository = TrashRepositoryImpl(trashDao, db, preferencesManager)
}