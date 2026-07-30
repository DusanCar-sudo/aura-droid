package dev.aura.auradroid.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aura.auradroid.data.network.Endpoint
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pairingStore by preferencesDataStore(name = "aura_pairing")

/**
 * Stores the paired desktop's address, and its token encrypted at rest.
 *
 * The token grants full control of the paired agent — it can read the user's
 * source tree and run shell commands — so it is encrypted with an AES-256-GCM
 * key held in the AndroidKeyStore, where the key material is non-exportable and
 * backed by hardware on most devices. Only the ciphertext reaches DataStore.
 *
 * Not androidx.security:security-crypto / EncryptedSharedPreferences: 1.1.0 has
 * been alpha for years, 1.0.0 is frozen, and Google has signalled deprecation.
 * This is ~80 lines of platform API with no such risk.
 *
 * Host and port are stored in the clear. They are not secrets, and having them
 * readable makes the pairing screen able to show what it is connected to
 * without a decrypt.
 */
@Singleton
class TokenVault @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun save(endpoint: Endpoint) {
        val (iv, ciphertext) = encrypt(endpoint.token, PAIRING_ALIAS)
        context.pairingStore.edit { prefs ->
            prefs[KEY_HOST] = endpoint.host
            prefs[KEY_PORT] = endpoint.port
            prefs[KEY_SECURE] = endpoint.secure
            prefs[KEY_TOKEN_IV] = iv
            prefs[KEY_TOKEN_CT] = ciphertext
            // Not a secret — it is a public certificate's digest — but it must
            // survive, because a LAN endpoint without it cannot be trusted.
            endpoint.certSha256?.let { prefs[KEY_CERT_SHA256] = it }
                ?: prefs.remove(KEY_CERT_SHA256)
        }
    }

    /** The paired endpoint, or null if never paired or the key is gone. */
    suspend fun load(): Endpoint? {
        val prefs = context.pairingStore.data.first()
        val host = prefs[KEY_HOST] ?: return null
        val port = prefs[KEY_PORT] ?: return null
        val iv = prefs[KEY_TOKEN_IV] ?: return null
        val ct = prefs[KEY_TOKEN_CT] ?: return null

        // A null here means the KeyStore entry is gone — the user cleared app
        // data, restored to a new device, or changed their lock screen on some
        // OEMs. The stored ciphertext is unrecoverable, so treat it as unpaired
        // rather than surfacing a decrypt error nobody can act on.
        val token = decrypt(iv, ct, PAIRING_ALIAS) ?: run {
            clearPairing()
            return null
        }

        val secure = prefs[KEY_SECURE] ?: false
        val cert = prefs[KEY_CERT_SHA256]

        // A secure pairing whose pin is missing cannot be honoured: connecting
        // anyway would accept any certificate on the network. Treat it as
        // unpaired so the user re-pairs and gets a pin.
        if (secure && cert == null) {
            clearPairing()
            return null
        }

        return Endpoint(
            host = host,
            port = port,
            token = token,
            secure = secure,
            certSha256 = cert,
        )
    }

    // ── Standalone credentials ──────────────────────────────────────────────
    //
    // Only present when the user turns standalone on. Until then nothing here
    // is written, so a phone that only ever pairs with a desktop holds no
    // provider credential at all — which was the original reason Settings had
    // no API-key field.

    data class Standalone(
        val apiKey: String,
        val baseUrl: String,
        val model: String,
    )

    /**
     * Store the provider credentials.
     *
     * A blank [Standalone.apiKey] keeps the key already stored, which is what
     * makes changing the model or the base URL not require typing a 50-character
     * secret again from a phone keyboard. Only an explicitly supplied key
     * replaces one.
     */
    suspend fun saveStandalone(creds: Standalone) {
        // The key is worth real money and is not revocable from this phone, so
        // it gets AndroidKeyStore encryption like the pairing token — but under
        // its own alias. Sharing one alias meant unpairing a desktop, which
        // deletes that alias, silently destroyed the API key too, and the user
        // had to type it again with nothing on screen explaining why.
        val encrypted = creds.apiKey.takeIf { it.isNotBlank() }?.let { encrypt(it, PROVIDER_ALIAS) }

        context.pairingStore.edit { prefs ->
            encrypted?.let { (iv, ct) ->
                prefs[KEY_API_IV] = iv
                prefs[KEY_API_CT] = ct
            }
            prefs[KEY_BASE_URL] = creds.baseUrl.trim()
            prefs[KEY_MODEL] = creds.model.trim()
            prefs[KEY_STANDALONE] = true
        }
    }

    suspend fun loadStandalone(): Standalone? {
        val prefs = context.pairingStore.data.first()
        if (prefs[KEY_STANDALONE] != true) return null
        val iv = prefs[KEY_API_IV] ?: return null
        val ct = prefs[KEY_API_CT] ?: return null

        // Its own alias first, then the pairing alias, because installs from
        // before the split hold a key encrypted under the old one. Found that
        // way it is re-encrypted immediately, so the fallback is needed exactly
        // once per device.
        val apiKey = decrypt(iv, ct, PROVIDER_ALIAS)
            ?: decrypt(iv, ct, PAIRING_ALIAS)?.also { migrateProviderKey(it) }
            // A missing KeyStore entry means the ciphertext is unrecoverable, so
            // treat standalone as unconfigured rather than surfacing a decrypt
            // error the user cannot act on.
            ?: run { clearStandalone(); return null }

        return Standalone(
            apiKey = apiKey,
            baseUrl = prefs[KEY_BASE_URL].orEmpty(),
            model = prefs[KEY_MODEL].orEmpty(),
        )
    }

    private suspend fun migrateProviderKey(apiKey: String) {
        val (iv, ct) = encrypt(apiKey, PROVIDER_ALIAS)
        context.pairingStore.edit { prefs ->
            prefs[KEY_API_IV] = iv
            prefs[KEY_API_CT] = ct
        }
    }

    /** What Settings can show without decrypting anything. */
    suspend fun standaloneSummary(): Pair<String, String>? {
        val prefs = context.pairingStore.data.first()
        if (prefs[KEY_STANDALONE] != true) return null
        return prefs[KEY_BASE_URL].orEmpty() to prefs[KEY_MODEL].orEmpty()
    }

    /**
     * The last base URL and model used, whether or not standalone is on.
     *
     * Only used to prefill the setup form. Someone turning standalone back on
     * is almost always going back to the same provider, and making them retype
     * an endpoint they have already entered once is friction for nothing —
     * neither value is a secret.
     */
    suspend fun lastProviderSettings(): Pair<String, String> {
        val prefs = context.pairingStore.data.first()
        return prefs[KEY_BASE_URL].orEmpty() to prefs[KEY_MODEL].orEmpty()
    }

    /** True when a provider key is stored, without decrypting it. */
    suspend fun hasProviderKey(): Boolean {
        val prefs = context.pairingStore.data.first()
        return prefs[KEY_API_IV] != null && prefs[KEY_API_CT] != null
    }

    suspend fun clearStandalone() {
        context.pairingStore.edit { prefs ->
            prefs.remove(KEY_API_IV)
            prefs.remove(KEY_API_CT)
            // Base URL and model deliberately survive: they are not secrets,
            // and keeping them means turning standalone back on is one field
            // rather than three.
            prefs[KEY_STANDALONE] = false
        }
        runCatching { keyStore().deleteEntry(PROVIDER_ALIAS) }
    }

    /**
     * Forget the paired desktop. Leaves the provider credentials alone —
     * unpairing a computer is not a reason to lose the API key that lets the
     * phone work without one.
     */
    suspend fun clearPairing() {
        context.pairingStore.edit { prefs ->
            prefs.remove(KEY_HOST)
            prefs.remove(KEY_PORT)
            prefs.remove(KEY_SECURE)
            prefs.remove(KEY_TOKEN_IV)
            prefs.remove(KEY_TOKEN_CT)
            prefs.remove(KEY_CERT_SHA256)
        }
        runCatching { keyStore().deleteEntry(PAIRING_ALIAS) }
    }

    /** Everything, for a full reset. */
    suspend fun clearAll() {
        context.pairingStore.edit { it.clear() }
        runCatching { keyStore().deleteEntry(PAIRING_ALIAS) }
        runCatching { keyStore().deleteEntry(PROVIDER_ALIAS) }
    }

    suspend fun isPaired(): Boolean = load() != null

    // ── Crypto ──────────────────────────────────────────────────────────────

    private fun encrypt(plaintext: String, alias: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(alias))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // GCM generates its own IV; it must be kept to decrypt, but is not secret.
        return cipher.iv.toB64() to ct.toB64()
    }

    private fun decrypt(ivB64: String, ctB64: String, alias: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            existingKey(alias) ?: return null,
            GCMParameterSpec(GCM_TAG_BITS, ivB64.fromB64()),
        )
        String(cipher.doFinal(ctB64.fromB64()), Charsets.UTF_8)
    }.getOrNull()

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(alias: String): SecretKey? =
        (keyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun secretKey(alias: String): SecretKey = existingKey(alias) ?: generateKey(alias)

    private fun generateKey(alias: String): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Not requiring device unlock: the socket reconnects in the
                // background after a network change, and a key that needs
                // authentication would break that with no security gain — an
                // attacker with the unlocked device has the app anyway.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private fun ByteArray.toB64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.fromB64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"

        /** Encrypts the paired desktop's token. Deleted on unpair. */
        const val PAIRING_ALIAS = "aura_pairing_token"

        /**
         * Encrypts the provider API key. Separate from the pairing alias so the
         * two have independent lifetimes — deleting one must not silently
         * destroy the other's ciphertext.
         */
        const val PROVIDER_ALIAS = "aura_provider_key"

        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128

        val KEY_HOST = stringPreferencesKey("host")
        val KEY_PORT = intPreferencesKey("port")
        val KEY_SECURE = booleanPreferencesKey("secure")
        val KEY_TOKEN_IV = stringPreferencesKey("token_iv")
        val KEY_TOKEN_CT = stringPreferencesKey("token_ct")
        val KEY_CERT_SHA256 = stringPreferencesKey("cert_sha256")
        val KEY_STANDALONE = booleanPreferencesKey("standalone")
        val KEY_API_IV = stringPreferencesKey("api_iv")
        val KEY_API_CT = stringPreferencesKey("api_ct")
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_MODEL = stringPreferencesKey("standalone_model")
    }
}
