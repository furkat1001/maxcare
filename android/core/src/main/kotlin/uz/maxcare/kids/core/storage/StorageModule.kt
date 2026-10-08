package uz.maxcare.kids.core.storage

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import uz.maxcare.kids.core.di.IoDispatcher

private const val SECURE_STORE_FILE = "maxcare_secure"

@Module
@InstallIn(SingletonComponent::class)
internal object StorageModule {
    // Один экземпляр на процесс: DataStore запрещает два активных хранилища на один файл.
    @Provides
    @Singleton
    fun provideSecureStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): SecureStore = DataStoreSecureStore(
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(SECURE_STORE_FILE) }
        ),
        aead = lazy { createKeystoreAead(context) },
        ioDispatcher = ioDispatcher
    )
}
