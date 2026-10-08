package uz.maxcare.kids.core.storage

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

private const val KEYSET_NAME = "maxcare_keyset"
private const val KEYSET_PREFS = "maxcare_keyset_prefs"
private const val MASTER_KEY_URI = "android-keystore://maxcare_master_key"

/**
 * Ключ данных (keyset Tink) хранится в SharedPreferences в зашифрованном виде;
 * его шифрует главный ключ из Android Keystore, который не покидает защищённое хранилище телефона.
 * При первом вызове оба ключа создаются.
 */
internal fun createKeystoreAead(context: Context): Aead {
    AeadConfig.register()
    val keysetHandle = AndroidKeysetManager.Builder()
        .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
        .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
        .withMasterKeyUri(MASTER_KEY_URI)
        .build()
        .keysetHandle
    return keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
}
