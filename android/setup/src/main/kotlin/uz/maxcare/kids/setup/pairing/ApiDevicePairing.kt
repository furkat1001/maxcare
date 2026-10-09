package uz.maxcare.kids.setup.pairing

import android.os.Build
import javax.inject.Inject
import uz.maxcare.kids.core.network.ApiResult
import uz.maxcare.kids.core.network.MaxCareApi
import uz.maxcare.kids.core.pairing.DevicePairing
import uz.maxcare.kids.core.pairing.PairingResult
import uz.maxcare.kids.core.storage.SecureKeys
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException
import uz.maxcare.kids.core.storage.writeString

/** Название телефона, которое родитель увидит в своём приложении. */
internal fun interface DeviceNameSource {
    fun deviceName(): String
}

internal class BuildDeviceNameSource @Inject constructor() : DeviceNameSource {
    override fun deviceName(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(MAX_NAME_LENGTH)

    private companion object {
        const val MAX_NAME_LENGTH = 64
    }
}

internal class ApiDevicePairing @Inject constructor(
    private val api: MaxCareApi,
    private val store: SecureStore,
    private val deviceNames: DeviceNameSource
) : DevicePairing {

    override suspend fun isPaired(): Boolean = try {
        store.read(SecureKeys.DEVICE_TOKEN) != null
    } catch (ignored: SecureStoreException) {
        // Токен не читается — с сервером телефон всё равно говорить не сможет; нужна новая
        // привязка, а она требует кода от родителя, так что обходом это не является.
        false
    }

    override suspend fun pair(code: String): PairingResult {
        val normalized = code.filterNot { it.isWhitespace() || it == '-' }
        return when {
            !CODE_PATTERN.matches(normalized) -> PairingResult.InvalidFormat

            isPaired() -> PairingResult.AlreadyPaired

            else -> when (val result = api.pairDevice(normalized, deviceNames.deviceName())) {
                is ApiResult.Ok -> {
                    // Токен — последним: по нему isPaired(), значит, при сбое посередине телефон
                    // останется непривязанным, а не привязанным без секрета.
                    store.writeString(SecureKeys.DEVICE_ID, result.value.deviceId)
                    store.write(SecureKeys.RECOVERY_SECRET, result.value.recoverySecret)
                    store.writeString(SecureKeys.DEVICE_TOKEN, result.value.deviceToken)
                    PairingResult.Paired
                }

                is ApiResult.HttpError -> httpError(result)

                ApiResult.NoConnection -> PairingResult.NoConnection

                ApiResult.InsecureConnection -> PairingResult.InsecureConnection

                ApiResult.BadResponse -> PairingResult.ServerError
            }
        }
    }

    private fun httpError(error: ApiResult.HttpError) = when {
        error.code == "invalid_code" -> PairingResult.InvalidCode
        error.status == HTTP_TOO_MANY_REQUESTS -> PairingResult.TooManyAttempts
        else -> PairingResult.ServerError
    }

    private companion object {
        // Только ASCII-цифры: \d в Kotlin пропустил бы и цифры других письменностей.
        val CODE_PATTERN = Regex("[0-9]{8}")
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
