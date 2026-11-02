package com.snib.app.household

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A small AES-GCM vault whose key lives in the Android Keystore and never leaves it.
 *
 * The household secret itself has to be readable by the app — we need the raw bytes to write new
 * tokens and to verify the ones we read — so it cannot *be* a Keystore key. It is instead sealed
 * by one. Result: the secret is at rest encrypted by hardware-backed material, is excluded from
 * backup, and does not survive a wipe of the app.
 */
internal object KeystoreVault {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "snib.vault.v1"
    private const val PREFS = "snib.vault"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun seal(context: Context, name: String, plaintext: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plaintext)
        prefs(context).edit()
            .putString(name, android.util.Base64.encodeToString(sealed, android.util.Base64.NO_WRAP))
            .commit()   // commit, not apply: this must be on disk before we say it is
    }

    fun open(context: Context, name: String): ByteArray? {
        val stored = prefs(context).getString(name, null) ?: return null
        val sealed = android.util.Base64.decode(stored, android.util.Base64.NO_WRAP)
        if (sealed.size <= IV_BYTES) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            }
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context, name: String) {
        prefs(context).edit().remove(name).commit()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
