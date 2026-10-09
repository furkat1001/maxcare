package uz.maxcare.kids.reports.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventSync
import uz.maxcare.kids.reports.EventUploader
import uz.maxcare.kids.reports.QueueingEventRecorder

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReportsModule {
    @Binds
    abstract fun bindEventRecorder(impl: QueueingEventRecorder): EventRecorder

    @Binds
    abstract fun bindEventSync(impl: EventUploader): EventSync
}
