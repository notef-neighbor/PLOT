package com.recall.android.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Mac
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class VaultCipher {
    private val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return listOf(cipher.iv, cipherText).joinToString(".") {
            Base64.encodeToString(it, Base64.NO_WRAP or Base64.URL_SAFE)
        }
    }

    fun decrypt(value: String): String {
        val (iv, cipherText) = value.split('.', limit = 2).map {
            Base64.decode(it, Base64.NO_WRAP or Base64.URL_SAFE)
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
    }

    fun sourceHash(source: String): String {
        val mac = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256)
        mac.init(getOrCreateHmacKey())
        return Base64.encodeToString(
            mac.doFinal(source.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP or Base64.URL_SAFE,
        )
    }

    @Synchronized
    private fun getOrCreateHmacKey(): SecretKey {
        val existing = keyStore.getKey(HMAC_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    HMAC_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                ).build(),
            )
            generateKey()
        }
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "recall_history_v1"
        private const val HMAC_ALIAS = "recall_source_hash_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
