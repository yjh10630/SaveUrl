package com.jinscompany.saveurl.di

import android.content.Context
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.room.BaseSaveUrlDao
import com.jinscompany.saveurl.data.room.CategoryDao
import com.jinscompany.saveurl.data.room.TrashDao
import com.jinscompany.saveurl.data.source.CategoryDBSource
import com.jinscompany.saveurl.data.source.CategoryDBSourceImpl
import com.jinscompany.saveurl.data.source.LocalUrlDBSource
import com.jinscompany.saveurl.data.source.LocalUrlDbSourceImpl
import com.jinscompany.saveurl.data.source.UrlParserSource
import com.jinscompany.saveurl.data.source.UrlParserSourceImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DataSourceImplModule {

    @Provides
    @Singleton
    fun provideLocalUrlDBSource(
        baseSaveUrlDao: BaseSaveUrlDao,
        categoryDao: CategoryDao,
        db: AppDatabase,
        trashDao: TrashDao
    ): LocalUrlDBSource = LocalUrlDbSourceImpl(baseSaveUrlDao, categoryDao, db, trashDao)

    @Provides
    @Singleton
    fun provideJsoupUrlParserSource(@ApplicationContext context: Context): UrlParserSource =
        UrlParserSourceImpl(context)

    @Provides
    @Singleton
    fun provideCategoryDBSource(
        categoryDao: CategoryDao,
        db: AppDatabase
    ): CategoryDBSource = CategoryDBSourceImpl(categoryDao, db)
}