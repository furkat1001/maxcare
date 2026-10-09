package uz.maxcare.kids.reports.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventSync
import uz.maxcare.kids.reports.EventSyncScheduler
import uz.maxcare.kids.reports.EventUploader
import uz.maxcare.kids.reports.QueueingEventRecorder
import uz.maxcare.kids.reports.WorkManagerEventSyncScheduler

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReportsModule {
    @Binds
    abstract fun bindEventRecorder(impl: QueueingEventRecorder): EventRecorder

    @Binds
    abstract fun bindEventSync(impl: EventUploader): EventSync

    @Binds
    abstract fun bindEventSyncScheduler(impl: WorkManagerEventSyncScheduler): EventSyncScheduler
}
