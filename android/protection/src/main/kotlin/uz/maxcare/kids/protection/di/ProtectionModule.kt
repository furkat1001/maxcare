package uz.maxcare.kids.protection.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import uz.maxcare.kids.core.protection.DeviceAdminActivation
import uz.maxcare.kids.core.protection.DeviceProtection
import uz.maxcare.kids.core.protection.ParentPassword
import uz.maxcare.kids.core.protection.PasswordRecovery
import uz.maxcare.kids.core.protection.ProtectionShutdown
import uz.maxcare.kids.protection.AndroidDeviceAdminActivation
import uz.maxcare.kids.protection.AndroidDevicePolicyGateway
import uz.maxcare.kids.protection.DevicePolicyGateway
import uz.maxcare.kids.protection.DevicePolicyProtection
import uz.maxcare.kids.protection.PasswordProtectedShutdown
import uz.maxcare.kids.protection.password.AndroidDeviceClock
import uz.maxcare.kids.protection.password.DeviceClock
import uz.maxcare.kids.protection.password.StoredParentPassword
import uz.maxcare.kids.protection.password.TotpPasswordRecovery
import uz.maxcare.kids.protection.service.AndroidProtectionServiceStarter
import uz.maxcare.kids.protection.service.ProtectionServiceStarter

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

    @Binds
    abstract fun bindProtectionShutdown(impl: PasswordProtectedShutdown): ProtectionShutdown

    @Binds
    abstract fun bindDeviceAdminActivation(
        impl: AndroidDeviceAdminActivation
    ): DeviceAdminActivation

    @Binds
    abstract fun bindProtectionServiceStarter(
        impl: AndroidProtectionServiceStarter
    ): ProtectionServiceStarter

    @Binds
    abstract fun bindPasswordRecovery(impl: TotpPasswordRecovery): PasswordRecovery

    companion object {
        /** Настенные часы — только для кодов восстановления (они привязаны к времени сервера). */
        @Provides
        fun provideWallClock(): Clock = Clock.systemUTC()
    }
}
