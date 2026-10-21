package uz.maxcare.kids.protection

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import uz.maxcare.kids.core.di.IoDispatcher
import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventType
import uz.maxcare.kids.core.protection.DisableProtectionResult
import uz.maxcare.kids.core.protection.ParentPassword
import uz.maxcare.kids.core.protection.PasswordCheck
import uz.maxcare.kids.core.protection.ProtectionShutdown
import uz.maxcare.kids.core.protection.ProtectionShutdownSignal

internal class PasswordProtectedShutdown @Inject constructor(
    private val parentPassword: ParentPassword,
    private val gateway: DevicePolicyGateway,
    private val remover: ProtectionRemover,
    private val events: EventRecorder,
    private val shutdownSignal: ProtectionShutdownSignal,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ProtectionShutdown {

    override suspend fun disable(password: CharArray): DisableProtectionResult {
        // Device Owner — тоже активный администратор, так что проверка покрывает оба режима.
        if (!withContext(ioDispatcher) { gateway.isAdminActive() }) {
            password.fill('\u0000')
            return DisableProtectionResult.NotActive
        }
        return when (val check = parentPassword.verify(password)) {
            PasswordCheck.Correct -> {
                // До снятия: запись сразу уходит на сервер, пока служба ещё работает.
                events.record(EventType.PROTECTION_DISABLED)
                withContext(ioDispatcher) { remover.removeAll() }
                // Служба доступности выключается сама: снять её отсюда Android не даёт.
                shutdownSignal.notifyDisabled()
                DisableProtectionResult.Disabled
            }

            else -> DisableProtectionResult.Rejected(check)
        }
    }
}
