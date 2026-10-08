package uz.maxcare.kids.core.storage

/**
 * Зашифрованное хранилище небольших секретов: токен устройства, хеш пароля родителя,
 * счётчик неудачных попыток. Шифрование — Tink (AES-256-GCM), ключ защищён Android Keystore.
 *
 * Не для скриншотов и текста с экрана: они анализируются только в памяти и на диск не пишутся.
 */
interface SecureStore {
    /**
     * @return значение или null, если по ключу ничего не записано.
     * @throws SecureStoreException если данные есть, но их нельзя прочитать или расшифровать.
     *   Это не то же самое, что «значения нет»: повреждённый хеш пароля не должен выглядеть
     *   как «пароль не задан».
     */
    suspend fun read(key: String): ByteArray?

    /** @throws SecureStoreException если не удалось зашифровать или записать. */
    suspend fun write(key: String, value: ByteArray)

    /** @throws SecureStoreException если не удалось записать изменения. */
    suspend fun remove(key: String)
}

class SecureStoreException(message: String, cause: Throwable) : Exception(message, cause)

suspend fun SecureStore.readString(key: String): String? = read(key)?.decodeToString()

suspend fun SecureStore.writeString(key: String, value: String) =
    write(key, value.encodeToByteArray())
