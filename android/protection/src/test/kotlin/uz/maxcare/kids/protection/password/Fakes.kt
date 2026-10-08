package uz.maxcare.kids.protection.password

import java.io.IOException
import kotlin.time.Duration
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException

internal class FakeSecureStore : SecureStore {
    val values = mutableMapOf<String, ByteArray>()

    /** Ключи, чтение и запись которых ведут себя как повреждённые данные. */
    val broken = mutableSetOf<String>()

    override suspend fun read(key: String): ByteArray? {
        failIfBroken(key)
        return values[key]?.copyOf()
    }

    override suspend fun write(key: String, value: ByteArray) {
        failIfBroken(key)
        values[key] = value.copyOf()
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    private fun failIfBroken(key: String) {
        if (key !in broken) return
        throw SecureStoreException("Cannot read '$key'", IOException("corrupted"))
    }
}

internal class FakeDeviceClock : DeviceClock {
    private var elapsed = 1_000_000L
    private var boot = 1

    override fun elapsedRealtime() = elapsed

    override fun bootCount() = boot

    fun advance(duration: Duration) {
        elapsed += duration.inWholeMilliseconds
    }

    /** После перезагрузки время с момента включения снова почти ноль. */
    fun reboot() {
        boot++
        elapsed = 5_000L
    }
}
