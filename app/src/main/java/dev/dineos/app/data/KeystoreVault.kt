package dev.dineos.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEY_ALIAS = "dineos-session"
private const val IV_BYTES = 12 // what AES-GCM generates by default
private val TOKENS = stringPreferencesKey("tokens")

/** The token pair, encrypted with a key that never leaves the Android Keystore (hardware-backed
 * on most phones), stored in DataStore. A refresh token is a long-lived credential: app-private
 * storage alone would hand it to anyone with a rooted phone or a backup of the app's data. */
class KeystoreVault(private val store: DataStore<Preferences>) : TokenVault {

    override suspend fun read(): Tokens? {
        val saved = store.data.first()[TOKENS] ?: return null
        return try {
            val bytes = Base64.decode(saved, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
            val (access, refresh) = String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)).split("\n")
            Tokens(access, refresh)
        } catch (_: GeneralSecurityException) {
            null // the key is gone (app data restored onto another phone): signed out, not a crash
        } catch (_: RuntimeException) {
            null // not what this version wrote
        }
    }

    override suspend fun write(tokens: Tokens?) {
        store.edit { prefs ->
            if (tokens == null) {
                prefs.remove(TOKENS)
            } else {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key()) // a fresh random IV every time
                val sealed = cipher.iv + cipher.doFinal("${tokens.access}\n${tokens.refresh}".toByteArray())
                prefs[TOKENS] = Base64.encodeToString(sealed, Base64.NO_WRAP)
            }
        }
    }

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
    }
}
