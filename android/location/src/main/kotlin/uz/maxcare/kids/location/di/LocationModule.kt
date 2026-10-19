package uz.maxcare.kids.location.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uz.maxcare.kids.core.location.LocationCollector
import uz.maxcare.kids.core.location.LocationProvider
import uz.maxcare.kids.core.location.LocationScheduler
import uz.maxcare.kids.core.location.LocationSync
import uz.maxcare.kids.location.AndroidLocationAccessProbe
import uz.maxcare.kids.location.AndroidLocationNotice
import uz.maxcare.kids.location.LocationAccessProbe
import uz.maxcare.kids.location.LocationManagerLocationProvider
import uz.maxcare.kids.location.LocationNotice
import uz.maxcare.kids.location.LocationUploader
import uz.maxcare.kids.location.PeriodicLocationCollector
import uz.maxcare.kids.location.WorkManagerLocationScheduler

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LocationModule {
    @Binds
    abstract fun bindLocationProvider(impl: LocationManagerLocationProvider): LocationProvider

    @Binds
    abstract fun bindLocationCollector(impl: PeriodicLocationCollector): LocationCollector

    @Binds
    abstract fun bindLocationSync(impl: LocationUploader): LocationSync

    @Binds
    abstract fun bindLocationScheduler(impl: WorkManagerLocationScheduler): LocationScheduler

    @Binds
    abstract fun bindLocationAccessProbe(impl: AndroidLocationAccessProbe): LocationAccessProbe

    @Binds
    abstract fun bindLocationNotice(impl: AndroidLocationNotice): LocationNotice
}
