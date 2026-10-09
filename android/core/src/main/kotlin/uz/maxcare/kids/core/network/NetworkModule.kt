package uz.maxcare.kids.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import uz.maxcare.kids.core.BuildConfig
import uz.maxcare.kids.core.di.IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    // Без HTTP-кэша: ответы содержат токены и секреты, на диск их не пишем.
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideMaxCareApi(
        client: OkHttpClient,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): MaxCareApi = OkHttpMaxCareApi(client, BuildConfig.API_BASE_URL.toHttpUrl(), ioDispatcher)

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 20L
    private const val CALL_TIMEOUT_SECONDS = 30L
}
