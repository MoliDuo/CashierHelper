package pro.xiangyu.cashierhelper.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConfigRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences by lazy { context.getSharedPreferences("config-repository-test", Context.MODE_PRIVATE) }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun `encrypts the key and restores the configuration`() = runBlocking {
        val repository = ConfigRepository(preferences, ReversingCipher)

        val saved = repository.save("https://cashier.example.com/", "sk_test_secret").getOrThrow()

        assertEquals("https://cashier.example.com", saved.baseUrl)
        assertFalse(preferences.all.values.any { it == "sk_test_secret" })
        // A new repository over the same storage reads it back.
        assertEquals(saved, ConfigRepository(preferences, ReversingCipher).config())
        assertEquals("https://cashier.example.com", repository.baseUrl())
    }

    @Test
    fun `nothing saved means missing`() = runBlocking {
        val repository = ConfigRepository(preferences, ReversingCipher)

        assertEquals(ConfigState.Missing(""), repository.current())
        assertNull(repository.config())
        assertNull(repository.baseUrl())
    }

    @Test
    fun `an invalid address or key is refused and nothing is stored`() = runBlocking {
        val repository = ConfigRepository(preferences, ReversingCipher)

        assertTrue(repository.save("http://cashier.example.com", "key").isFailure)
        assertTrue(repository.save("https://cashier.example.com", " ").isFailure)
        assertTrue(preferences.all.isEmpty())
    }

    @Test
    fun `a key that cannot be decrypted is reported, not hidden`() = runBlocking {
        preferences.edit()
            .putString("base_url", "https://cashier.example.com")
            .putString("api_key_ciphertext", "ciphertext")
            .putString("api_key_iv", "iv")
            .commit()

        val repository = ConfigRepository(preferences, ThrowingCipher)

        assertEquals(ConfigState.KeyUnreadable("https://cashier.example.com"), repository.current())
        assertNull(repository.config())
    }

    @Test
    fun `an invalid stored key counts as missing and saving fixes it`() = runBlocking {
        preferences.edit()
            .putString("base_url", "https://cashier.example.com")
            .putString("api_key_ciphertext", "ciphertext")
            .putString("api_key_iv", "iv")
            .commit()
        val repository = ConfigRepository(preferences, ReversingCipher)
        preferences.edit().putString("api_key_ciphertext", "has space").commit()
        assertEquals(ConfigState.Missing("https://cashier.example.com"), repository.current())

        repository.save("https://cashier.example.com", "fresh-key").getOrThrow()

        assertEquals(ConfigState.Ready(AppConfig("https://cashier.example.com", "fresh-key")), repository.current())
    }

    @Test
    fun `a stored cleartext address is not usable`() = runBlocking {
        ConfigRepository(preferences, ReversingCipher).save("https://cashier.example.com", "key").getOrThrow()
        preferences.edit().putString("base_url", "http://cashier.example.com").commit()

        val repository = ConfigRepository(preferences, ReversingCipher)

        assertEquals(ConfigState.Missing("http://cashier.example.com"), repository.current())
        assertNull(repository.baseUrl())
    }

    @Test
    fun `the state flow follows a save`() = runBlocking {
        val repository = ConfigRepository(preferences, ReversingCipher)
        assertNull(repository.state.value)

        repository.current()
        repository.save("https://cashier.example.com", "key").getOrThrow()

        assertEquals(ConfigState.Ready(AppConfig("https://cashier.example.com", "key")), repository.state.value)
    }

    private object ReversingCipher : SecretCipher {
        override fun encrypt(value: String) = EncryptedSecret(value.reversed(), "test-iv")

        override fun decrypt(secret: EncryptedSecret): String = secret.ciphertext.reversed()
    }

    private object ThrowingCipher : SecretCipher {
        override fun encrypt(value: String): EncryptedSecret = error("keystore unavailable")

        override fun decrypt(secret: EncryptedSecret): String = error("keystore unavailable")
    }
}
