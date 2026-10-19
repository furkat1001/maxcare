package uz.maxcare.kids.protection

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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

    /** Постоянный VPN фильтра с блокировкой трафика без него (только Device Owner); false — снять. */
    fun setAlwaysOnVpn(enabled: Boolean)

    /**
     * true — выдать себе доступ к геолокации (включая «в фоне»), запретить его отзыв и включить
     * геолокацию телефона (только Device Owner). Доступ закрепляется с Android 12 только если при
     * настройке Device Owner не отказались от выдачи разрешений на датчики.
     * false — вернуть доступ под управление пользователя; саму геолокацию не выключает.
     */
    fun setLocationLocked(locked: Boolean)
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

    override fun setAlwaysOnVpn(enabled: Boolean) {
        try {
            dpm.setAlwaysOnVpnPackage(admin, if (enabled) context.packageName else null, true)
        } catch (ignored: PackageManager.NameNotFoundException) {
            // Наш собственный пакет есть всегда; на случай невозможного — защита работает и без always-on.
        } catch (ignored: UnsupportedOperationException) {
            // Прошивка не поддерживает always-on VPN: фильтр запускается обычным способом.
        }
    }

    override fun setLocationLocked(locked: Boolean) {
        if (!locked) {
            // Разрешение остаётся выданным, но пользователь снова может его отозвать.
            setLocationGrantState(DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT)
            return
        }
        // Сначала доступ, затем сама геолокация: иначе первый фикс снова упрётся в разрешение.
        val sensorsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            dpm.canAdminGrantSensorsPermissions()
        if (sensorsAllowed) {
            setLocationGrantState(
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dpm.setLocationEnabled(admin, true)
            } else {
                // До Android 11 у владельца есть только общая настройка режима геолокации.
                @Suppress("DEPRECATION")
                dpm.setSecureSetting(
                    admin,
                    Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_HIGH_ACCURACY.toString()
                )
            }
        } catch (ignored: SecurityException) {
            // Прошивка не дала владельцу менять геолокацию: остаётся мониторинг (location_off)
            // и уведомление ребёнку, а остальная защита не должна из-за этого отключиться.
        }
    }

    private fun setLocationGrantState(state: Int) = locationPermissions().forEach {
        dpm.setPermissionGrantState(admin, context.packageName, it, state)
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

private fun locationPermissions() = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
}
