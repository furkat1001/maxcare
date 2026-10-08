package uz.maxcare.kids.protection

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Тонкая обёртка над [DevicePolicyManager]: вся логика — в [DevicePolicyProtection],
 * которая тестируется на JVM с фейковой реализацией.
 */
internal interface DevicePolicyGateway {
    fun isAdminActive(): Boolean
    fun isDeviceOwner(): Boolean
    fun setOwnUninstallBlocked(blocked: Boolean)
    fun addUserRestriction(restriction: String)
    fun clearUserRestriction(restriction: String)
    fun clearDeviceOwner()
}

internal class AndroidDevicePolicyGateway @Inject constructor(
    @ApplicationContext private val context: Context
) : DevicePolicyGateway {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, MaxCareAdminReceiver::class.java)

    override fun isAdminActive(): Boolean = dpm.isAdminActive(admin)

    override fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    override fun setOwnUninstallBlocked(blocked: Boolean) {
        dpm.setUninstallBlocked(admin, context.packageName, blocked)
    }

    override fun addUserRestriction(restriction: String) {
        dpm.addUserRestriction(admin, restriction)
    }

    override fun clearUserRestriction(restriction: String) {
        dpm.clearUserRestriction(admin, restriction)
    }

    // Единственный способ снять Device Owner без сброса телефона. Вызывается только из debug-сборки.
    @Suppress("DEPRECATION")
    override fun clearDeviceOwner() {
        dpm.clearDeviceOwnerApp(context.packageName)
    }
}
