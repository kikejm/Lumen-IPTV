package app.lumentv.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra secretos (contraseñas IPTV, claves privadas WireGuard) con AES-256-GCM
 * usando una clave no exportable del Android Keystore.
 *
 * Formato: `k1:<base64(iv || ciphertext)>`. Si el Keystore falla (algunos Fire OS
 * antiguos lo tienen roto) se guarda como `p1:<base64(plaintext)>` para no perder datos.
 */
class SecretBox(private val alias: String = "lumen_secret_box_v1") {

    private val key: SecretKey? by lazy { runCatching { loadOrCreateKey() }.onFailure { Log.w(TAG, "Keystore no disponible", it) }.getOrNull() }

    fun encrypt(plain: String): String {
        val k = key ?: return "p1:" + b64(plain.toByteArray())
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, k)
            "k1:" + b64(cipher.iv + cipher.doFinal(plain.toByteArray()))
        }.getOrElse { "p1:" + b64(plain.toByteArray()) }
    }

    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        return when {
            stored.startsWith("p1:") -> String(unb64(stored.removePrefix("p1:")))
            stored.startsWith("k1:") -> {
                val all = unb64(stored.removePrefix("k1:"))
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, requireNotNull(key), GCMParameterSpec(128, all, 0, IV_SIZE))
                String(cipher.doFinal(all, IV_SIZE, all.size - IV_SIZE))
            }
            else -> stored // valor heredado sin cifrar
        }
    }

    private fun loadOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val TAG = "SecretBox"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
