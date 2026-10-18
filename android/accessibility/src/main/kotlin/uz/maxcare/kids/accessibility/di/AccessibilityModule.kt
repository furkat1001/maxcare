package uz.maxcare.kids.accessibility.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import uz.maxcare.kids.accessibility.BaseDictionary
import uz.maxcare.kids.accessibility.DictionarySource
import uz.maxcare.kids.accessibility.limits.AppLimitEnforcer
import uz.maxcare.kids.core.limits.TimeLimitExtension

@Module
@InstallIn(SingletonComponent::class)
internal object AccessibilityModule {

    @Provides
    @Singleton
    fun provideBaseDictionary(@ApplicationContext context: Context): BaseDictionary =
        BaseDictionary(DictionarySource.loadStems(context))
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AccessibilityBindings {

    /** Экран «время вышло» (модуль blocker) даёт отсрочку через интерфейс из core. */
    @Binds
    abstract fun bindTimeLimitExtension(impl: AppLimitEnforcer): TimeLimitExtension
}
