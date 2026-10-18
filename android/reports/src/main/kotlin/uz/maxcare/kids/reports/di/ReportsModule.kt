package uz.maxcare.kids.reports.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventSync
import uz.maxcare.kids.core.limits.TodayUsage
import uz.maxcare.kids.core.usage.AppUsageScheduler
import uz.maxcare.kids.core.usage.AppUsageSync
import uz.maxcare.kids.reports.EventSyncScheduler
import uz.maxcare.kids.reports.EventUploader
import uz.maxcare.kids.reports.QueueingEventRecorder
import uz.maxcare.kids.reports.WorkManagerEventSyncScheduler
import uz.maxcare.kids.reports.usage.AndroidUsageHistory
import uz.maxcare.kids.reports.usage.AppUsageUploader
import uz.maxcare.kids.reports.usage.UsageHistory
import uz.maxcare.kids.reports.usage.UsageStatsTodayUsage
import uz.maxcare.kids.reports.usage.WorkManagerAppUsageScheduler

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReportsModule {
    @Binds
    abstract fun bindEventRecorder(impl: QueueingEventRecorder): EventRecorder

    @Binds
    abstract fun bindEventSync(impl: EventUploader): EventSync

    @Binds
    abstract fun bindEventSyncScheduler(impl: WorkManagerEventSyncScheduler): EventSyncScheduler

    @Binds
    abstract fun bindUsageHistory(impl: AndroidUsageHistory): UsageHistory

    @Binds
    abstract fun bindAppUsageSync(impl: AppUsageUploader): AppUsageSync

    @Binds
    abstract fun bindAppUsageScheduler(impl: WorkManagerAppUsageScheduler): AppUsageScheduler

    @Binds
    abstract fun bindTodayUsage(impl: UsageStatsTodayUsage): TodayUsage
}
