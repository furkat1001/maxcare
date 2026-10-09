package uz.maxcare.kids.setup.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uz.maxcare.kids.core.pairing.DevicePairing
import uz.maxcare.kids.setup.pairing.ApiDevicePairing
import uz.maxcare.kids.setup.pairing.BuildDeviceNameSource
import uz.maxcare.kids.setup.pairing.DeviceNameSource

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SetupModule {
    @Binds
    abstract fun bindDevicePairing(impl: ApiDevicePairing): DevicePairing

    @Binds
    abstract fun bindDeviceNameSource(impl: BuildDeviceNameSource): DeviceNameSource
}
