package uz.maxcare.kids.core.filter

import android.content.Intent

/**
 * Фильтр сайтов: локальный VPN, через который проходят только DNS-запросы телефона.
 * Запускается и останавливается вместе с защитой (`DeviceProtection.applyPolicies`).
 */
interface NetworkFilter {
    /** Есть согласие на VPN — дано пользователем или выдано режимом Device Owner. */
    fun isPermitted(): Boolean

    /** Системный диалог согласия на VPN; null — согласие уже есть. */
    fun permissionIntent(): Intent?

    /** Запустить, если есть согласие; повторный вызов ничего не меняет. */
    fun start()

    fun stop()

    /** Проверить обходы фильтра (например, «Частный DNS» с адресом сервера) и записать события. */
    suspend fun checkBypasses()
}
