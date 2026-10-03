package pro.xiangyu.cashierhelper.config

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface ConfigState {
    /** Nothing usable is saved. [baseUrl] is whatever address was typed or stored, to prefill the form. */
    data class Missing(val baseUrl: String = "") : ConfigState

    /** An address and a key are stored but the key cannot be decrypted, e.g. after a keystore reset. */
    data class KeyUnreadable(val baseUrl: String) : ConfigState

    data class Ready(val config: AppConfig) : ConfigState
}

/**
 * The saved connection. The key is encrypted with an Android Keystore key and
 * read once; the result is cached. A key that cannot be decrypted is reported
 * as such, never silently treated as "not configured", so the screen can tell
 * her to enter it again.
 */
class ConfigRepository(
    private val preferences: SharedPreferences,
    private val cipher: SecretCipher,
) {
    constructor(context: Context) : this(
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        cipher = AndroidKeystoreSecretCipher(),
    )

    private val mutableState = MutableStateFlow<ConfigState?>(null)

    /** Null until the first load has finished. */
    val state: StateFlow<ConfigState?> = mutableState.asStateFlow()

    suspend fun current(): ConfigState {
        mutableState.value?.let { return it }
        val loaded = withContext(Dispatchers.IO) { read() }
        // A save that finished meanwhile wins over the value read before it.
        mutableState.compareAndSet(null, loaded)
        return checkNotNull(mutableState.value)
    }

    suspend fun config(): AppConfig? = (current() as? ConfigState.Ready)?.config

    /** The stored address, without touching the keystore. Not secret. */
    fun baseUrl(): String? =
        preferences.getString(KEY_BASE_URL, null)?.let { BaseUrlValidator.normalize(it).getOrNull() }

    suspend fun save(baseUrl: String, apiKey: String): Result<AppConfig> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = BaseUrlValidator.normalize(baseUrl).getOrThrow()
            val normalizedKey = ApiKeyValidator.normalize(apiKey).getOrThrow()
            val encrypted = cipher.encrypt(normalizedKey)
            check(
                preferences.edit()
                    .putString(KEY_BASE_URL, normalizedUrl)
                    .putString(KEY_API_CIPHERTEXT, encrypted.ciphertext)
                    .putString(KEY_API_IV, encrypted.initializationVector)
                    .commit(),
            ) { "无法保存设置" }
            AppConfig(normalizedUrl, normalizedKey).also { mutableState.value = ConfigState.Ready(it) }
        }
    }

    private fun read(): ConfigState {
        val storedUrl = preferences.getString(KEY_BASE_URL, "").orEmpty()
        val ciphertext = preferences.getString(KEY_API_CIPHERTEXT, null)
        val iv = preferences.getString(KEY_API_IV, null)
        if (ciphertext == null || iv == null) return ConfigState.Missing(storedUrl)

        val key = runCatching { cipher.decrypt(EncryptedSecret(ciphertext, iv)) }
            .getOrNull() ?: return ConfigState.KeyUnreadable(storedUrl)
        val url = BaseUrlValidator.normalize(storedUrl).getOrNull()
        val normalizedKey = ApiKeyValidator.normalize(key).getOrNull()
        return if (url != null && normalizedKey != null) {
            ConfigState.Ready(AppConfig(url, normalizedKey))
        } else {
            ConfigState.Missing(storedUrl)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "secure_config"
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_CIPHERTEXT = "api_key_ciphertext"
        const val KEY_API_IV = "api_key_iv"
    }
}
