package com.jinscompany.saveurl.di

import com.jinscompany.saveurl.data.revisit.AndroidReminderAlarms
import com.jinscompany.saveurl.data.revisit.ReminderAlarms
import com.jinscompany.saveurl.data.revisit.RevisitClock
import com.jinscompany.saveurl.data.revisit.SystemRevisitClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 다시 보게 만들기 (42번): 알람·시계 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RevisitModule {
    @Binds
    abstract fun bindReminderAlarms(impl: AndroidReminderAlarms): ReminderAlarms

    @Binds
    abstract fun bindRevisitClock(impl: SystemRevisitClock): RevisitClock
}
