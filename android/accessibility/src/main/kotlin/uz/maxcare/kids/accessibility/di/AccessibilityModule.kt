package uz.maxcare.kids.accessibility.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import uz.maxcare.kids.accessibility.DictionarySource
import uz.maxcare.kids.accessibility.ForbiddenWords

@Module
@InstallIn(SingletonComponent::class)
internal object AccessibilityModule {

    @Provides
    @Singleton
    fun provideForbiddenWords(@ApplicationContext context: Context): ForbiddenWords =
        ForbiddenWords(DictionarySource.loadStems(context))
}
