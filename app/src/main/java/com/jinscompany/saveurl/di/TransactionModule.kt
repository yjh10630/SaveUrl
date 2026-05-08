package com.jinscompany.saveurl.di

import com.jinscompany.saveurl.data.transaction.RoomTransactionRunner
import com.jinscompany.saveurl.domain.transaction.TransactionRunner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TransactionModule {
    @Singleton
    @Binds
    abstract fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner
}
