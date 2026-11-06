package com.vel.app.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Representación del resultado de un cifrado autenticado AES-GCM.
 * Contiene el vector de inicialización (nonce de 96 bits) y el texto cifrado
 * junto con su etiqueta de autenticación (tag de 128 bits).
 */
data class EncryptedPayload(
    val iv: ByteArray,
    val ciphertextWithTag: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedPayload
        if (!iv.contentEquals(other.iv)) return false
        if (!ciphertextWithTag.contentEquals(other.ciphertextWithTag)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = iv.contentHashCode()
        result = 31 * result + ciphertextWithTag.contentHashCode()
        return result
    }
}

/**
 * Módulo de cifrado autenticado AES/GCM/NoPadding estándar de 256 bits.
 * - Longitud de clave: 256 bits (32 bytes).
 * - Longitud de nonce/IV: 96 bits (12 bytes) generado aleatoriamente para cada operación.
 * - Longitud de etiqueta de autenticación: 128 bits (16 bytes).
 * No reutiliza nonces bajo ninguna circunstancia.
 */
object AesGcm {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ALGORITHM = "AES"
    const val KEY_SIZE_BYTES = 32 // 256 bits
    const val IV_SIZE_BYTES = 12   // 96 bits
    const val TAG_LENGTH_BITS = 128

    private val secureRandom = SecureRandom()

    /**
     * Cifra datos en claro utilizando AES-GCM de 256 bits y genera un IV único de 96 bits.
     * @param key Clave simétrica de 256 bits (32 bytes).
     * @param plaintext Datos en claro a proteger.
     * @param associatedData Datos adicionales autenticados (opcionales, no cifrados pero protegidos contra alteración).
     * @return [EncryptedPayload] con el IV generado y el texto cifrado autenticado.
     */
    fun encrypt(
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray? = null
    ): EncryptedPayload {
        require(key.size == KEY_SIZE_BYTES) {
            "La clave AES debe tener exactamente 256 bits (32 bytes). Tamaño recibido: ${key.size}"
        }

        val iv = ByteArray(IV_SIZE_BYTES)
        secureRandom.nextBytes(iv)

        val secretKey = SecretKeySpec(key, ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }

        val ciphertextWithTag = cipher.doFinal(plaintext)
        return EncryptedPayload(iv = iv, ciphertextWithTag = ciphertextWithTag)
    }

    /**
     * Descifra y autentica un texto cifrado con AES-GCM.
     * Si los datos o la etiqueta han sido alterados, la verificación fallará con [GeneralSecurityException].
     * @param key Clave simétrica de 256 bits.
     * @param iv Vector de inicialización de 96 bits utilizado durante el cifrado.
     * @param ciphertextWithTag Texto cifrado con etiqueta de autenticación al final.
     * @param associatedData Datos adicionales autenticados requeridos para validar la autenticidad.
     * @return Los datos descifrados originales.
     * @throws GeneralSecurityException si la autenticación falla o los datos están alterados.
     */
    @Throws(GeneralSecurityException::class)
    fun decrypt(
        key: ByteArray,
        iv: ByteArray,
        ciphertextWithTag: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        require(key.size == KEY_SIZE_BYTES) {
            "La clave AES debe tener exactamente 256 bits (32 bytes)."
        }
        require(iv.size == IV_SIZE_BYTES) {
            "El vector de inicialización (IV) debe tener exactamente 96 bits (12 bytes)."
        }

        val secretKey = SecretKeySpec(key, ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }

        return cipher.doFinal(ciphertextWithTag)
    }
}
