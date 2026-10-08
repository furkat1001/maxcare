package uz.maxcare.kids.protection.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uz.maxcare.kids.core.protection.DeviceProtection
import uz.maxcare.kids.core.protection.ParentPassword
import uz.maxcare.kids.protection.AndroidDevicePolicyGateway
import uz.maxcare.kids.protection.DevicePolicyGateway
import uz.maxcare.kids.protection.DevicePolicyProtection
import uz.maxcare.kids.protection.password.AndroidDeviceClock
import uz.maxcare.kids.protection.password.DeviceClock
import uz.maxcare.kids.protection.password.StoredParentPassword

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ProtectionModule {
    @Binds
    abstract fun bindDeviceProtection(impl: DevicePolicyProtection): DeviceProtection

    @Binds
    abstract fun bindDevicePolicyGateway(impl: AndroidDevicePolicyGateway): DevicePolicyGateway

    @Binds
    abstract fun bindParentPassword(impl: StoredParentPassword): ParentPassword

    @Binds
    abstract fun bindDeviceClock(impl: AndroidDeviceClock): DeviceClock
}
