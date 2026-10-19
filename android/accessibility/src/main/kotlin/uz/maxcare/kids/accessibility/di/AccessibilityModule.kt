package uz.maxcare.kids.accessibility.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import uz.maxcare.kids.accessibility.BaseDictionary
import uz.maxcare.kids.accessibility.DictionarySource

@Module
@InstallIn(SingletonComponent::class)
internal object AccessibilityModule {

    @Provides
    @Singleton
    fun provideBaseDictionary(@ApplicationContext context: Context): BaseDictionary =
        BaseDictionary(DictionarySource.loadStems(context))
}
