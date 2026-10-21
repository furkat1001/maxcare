package uz.maxcare.kids.core.protection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Родитель отключил защиту паролем. Администратор и VPN снимает модуль защиты, а служба
 * доступности (фильтр текста и картинок, ограничения времени) включается отдельным переключателем
 * Android — выключить её может только она сама, поэтому она слушает этот сигнал.
 *
 * Только событие в памяти процесса, без флага на диске: если родитель потом включит службу
 * вручную раньше администратора, она не должна выключаться сама.
 */
interface ProtectionShutdownSignal {
    val shutdowns: Flow<Unit>

    fun notifyDisabled()
}

class InMemoryProtectionShutdownSignal : ProtectionShutdownSignal {
    // Буфер: подписчик (служба) может обрабатывать сигнал на другом потоке.
    private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val shutdowns: Flow<Unit> = events.asSharedFlow()

    override fun notifyDisabled() {
        events.tryEmit(Unit)
    }
}
