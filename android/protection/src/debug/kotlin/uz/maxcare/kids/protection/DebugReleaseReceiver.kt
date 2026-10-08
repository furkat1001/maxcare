package uz.maxcare.kids.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Снять Device Owner на тестовом телефоне (только debug-сборка):
 * ```
 * adb shell am broadcast -n uz.maxcare.kids/uz.maxcare.kids.protection.DebugReleaseReceiver
 * ```
 * Ресивер закрыт разрешением DUMP: оно есть у adb shell, но не у приложений на телефоне.
 */
@AndroidEntryPoint
class DebugReleaseReceiver : BroadcastReceiver() {
    @Inject
    internal lateinit var deviceOwnerRelease: DeviceOwnerRelease

    override fun onReceive(context: Context, intent: Intent) {
        resultData = if (deviceOwnerRelease.release()) "released" else "not a device owner"
    }
}
