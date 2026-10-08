package uz.maxcare.kids.protection

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
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

    /** Отключить обычного администратора устройства (не Device Owner). */
    fun removeActiveAdmin()

    /**
     * Выдать себе разрешение на уведомления и запретить его отзыв (только Device Owner).
     * До Android 13 разрешения нет — ничего не делает.
     */
    fun lockNotificationPermissionGranted()
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

    // Единственный способ снять Device Owner без сброса телефона.
    // Вызывается только после пароля родителя или из debug-команды.
    @Suppress("DEPRECATION")
    override fun clearDeviceOwner() {
        dpm.clearDeviceOwnerApp(context.packageName)
    }

    override fun removeActiveAdmin() {
        dpm.removeActiveAdmin(admin)
    }

    override fun lockNotificationPermissionGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        dpm.setPermissionGrantState(
            admin,
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        )
    }
}
