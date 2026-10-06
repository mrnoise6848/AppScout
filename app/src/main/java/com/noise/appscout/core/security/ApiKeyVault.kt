package com.noise.appscout.core.security

import android.content.Context
import androidx.core.content.edit
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small AES-GCM vault backed by the Android Keystore for the user's Gemini API key.
 *
 * Why not `security-crypto`: it is deprecated (ADR-005 companion decision). Only the IV and the
 * ciphertext are written to private preferences — the key material never leaves the Keystore and
 * the plaintext never touches a file we own.
 */
@Singleton
class ApiKeyVault @Inject constructor(
    @ApplicationContext context: Context,
) : com.noise.appscout.domain.ai.AiApiKeyProvider {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun readApiKey(): String? = read()

    fun hasKey(): Boolean = !read().isNullOrEmpty()

    fun save(plaintext: String) {
        val trimmed = plaintext.trim()
        if (trimmed.isEmpty()) {
            clear()
            return
        }
        val encoded = encrypt(trimmed)
        prefs.edit { putString(KEY_SECRET, encoded) }
    }

    fun read(): String? {
        val stored = prefs.getString(KEY_SECRET, null) ?: return null
        return runCatching { decrypt(stored) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun clear() {
        prefs.edit { remove(KEY_SECRET) }
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + ciphertext
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = payload.copyOfRange(GCM_IV_LENGTH, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "appscout_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
        const val PREFS = "appscout_secure_vault"
        const val KEY_SECRET = "gemini_api_key"
    }
}
