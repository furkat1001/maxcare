package uz.maxcare.kids.core.protection

import android.content.Intent

/** Включение администратора устройства в обычном режиме (установка из Google Play). */
interface DeviceAdminActivation {
    /**
     * Интент системного экрана «Активировать администратора» с объяснением, зачем он нужен.
     * Запускать через `startActivityForResult` или Activity Result API: результат — согласие пользователя.
     */
    fun activationIntent(): Intent
}
