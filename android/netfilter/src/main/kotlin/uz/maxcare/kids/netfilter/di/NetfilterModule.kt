package uz.maxcare.kids.netfilter.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uz.maxcare.kids.core.filter.NetworkFilter
import uz.maxcare.kids.netfilter.AndroidBlockNotifier
import uz.maxcare.kids.netfilter.AndroidNetworkFilter
import uz.maxcare.kids.netfilter.AndroidPrivateDnsProbe
import uz.maxcare.kids.netfilter.BlockNotifier
import uz.maxcare.kids.netfilter.ElapsedClock
import uz.maxcare.kids.netfilter.PrivateDnsProbe
import uz.maxcare.kids.netfilter.SystemElapsedClock

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetfilterModule {
    @Binds
    abstract fun bindNetworkFilter(impl: AndroidNetworkFilter): NetworkFilter

    @Binds
    abstract fun bindElapsedClock(impl: SystemElapsedClock): ElapsedClock

    @Binds
    abstract fun bindBlockNotifier(impl: AndroidBlockNotifier): BlockNotifier

    @Binds
    abstract fun bindPrivateDnsProbe(impl: AndroidPrivateDnsProbe): PrivateDnsProbe
}
