package uz.maxcare.kids.core.di

import android.os.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import uz.maxcare.kids.core.protection.InMemoryProtectionShutdownSignal
import uz.maxcare.kids.core.protection.ProtectionShutdownSignal
import uz.maxcare.kids.core.protection.TimedTrustedSettingsWindow
import uz.maxcare.kids.core.protection.TrustedSettingsWindow

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    /**
     * Настенные часы (UTC): время событий и коды восстановления. Ребёнок может их перевести —
     * поэтому блокировки ввода считаются не по ним, а по времени с момента включения телефона.
     */
    @Provides
    fun provideWallClock(): Clock = Clock.systemUTC()

    /** Один на процесс: его открывает мастер или уведомление, а читает служба доступности. */
    @Provides
    @Singleton
    fun provideTrustedSettingsWindow(): TrustedSettingsWindow =
        TimedTrustedSettingsWindow { SystemClock.elapsedRealtime() }

    /** Один на процесс: подаёт экран отключения защиты, слушает служба доступности. */
    @Provides
    @Singleton
    fun provideProtectionShutdownSignal(): ProtectionShutdownSignal =
        InMemoryProtectionShutdownSignal()
}
