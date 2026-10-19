package uz.maxcare.kids.ml.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uz.maxcare.kids.core.ml.ImageClassifier
import uz.maxcare.kids.ml.LiteRtImageClassifier

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MlModule {
    @Binds
    abstract fun bindImageClassifier(impl: LiteRtImageClassifier): ImageClassifier
}
