package uz.maxcare.kids.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    /**
     * Настенные часы (UTC): время событий и коды восстановления. Ребёнок может их перевести —
     * поэтому блокировки ввода считаются не по ним, а по времени с момента включения телефона.
     */
    @Provides
    fun provideWallClock(): Clock = Clock.systemUTC()
}
