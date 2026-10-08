package uz.maxcare.kids.protection

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import uz.maxcare.kids.core.di.IoDispatcher
import uz.maxcare.kids.core.protection.DisableProtectionResult
import uz.maxcare.kids.core.protection.ParentPassword
import uz.maxcare.kids.core.protection.PasswordCheck
import uz.maxcare.kids.core.protection.ProtectionShutdown

internal class PasswordProtectedShutdown @Inject constructor(
    private val parentPassword: ParentPassword,
    private val gateway: DevicePolicyGateway,
    private val remover: ProtectionRemover,
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
                withContext(ioDispatcher) { remover.removeAll() }
                DisableProtectionResult.Disabled
            }

            else -> DisableProtectionResult.Rejected(check)
        }
    }
}
