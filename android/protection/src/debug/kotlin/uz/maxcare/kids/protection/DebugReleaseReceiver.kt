package uz.maxcare.kids.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Снять защиту на тестовом телефоне без пароля (только debug-сборка):
 * ```
 * adb shell am broadcast -n uz.maxcare.kids/uz.maxcare.kids.protection.DebugReleaseReceiver
 * ```
 * Ресивер закрыт разрешением DUMP: оно есть у adb shell, но не у приложений на телефоне.
 * В release-сборке защиту снимает только родитель паролем ([PasswordProtectedShutdown]).
 */
@AndroidEntryPoint
class DebugReleaseReceiver : BroadcastReceiver() {
    @Inject
    internal lateinit var protectionRemover: ProtectionRemover

    override fun onReceive(context: Context, intent: Intent) {
        resultData = if (protectionRemover.removeAll()) "released" else "protection not active"
    }
}
