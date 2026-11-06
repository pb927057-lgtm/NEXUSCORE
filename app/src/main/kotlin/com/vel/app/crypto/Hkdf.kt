package com.vel.app.crypto

import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Implementación estándar de HKDF (RFC 5869) basada en HMAC-SHA-256.
 * Utilizada para derivar claves de sesión y códigos de autenticación cortos (SAS).
 */
object Hkdf {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val HASH_LEN = 32 // SHA-256 genera salidas de 32 bytes

    /**
     * Paso Extract: PRK = HMAC-Hash(salt, IKM)
     */
    fun extract(salt: ByteArray?, ikm: ByteArray): ByteArray {
        val effectiveSalt = if (salt == null || salt.isEmpty()) {
            ByteArray(HASH_LEN)
        } else {
            salt
        }
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(effectiveSalt, HMAC_ALGORITHM))
        return mac.doFinal(ikm)
    }

    /**
     * Paso Expand: OKM = T(1) | T(2) | ... | T(N)
     */
    fun expand(prk: ByteArray, info: ByteArray?, length: Int): ByteArray {
        require(length <= 255 * HASH_LEN) { "La longitud solicitada excede el límite de HKDF-SHA-256" }
        require(length > 0) { "La longitud solicitada debe ser mayor a 0" }

        val n = Math.ceil(length.toDouble() / HASH_LEN).toInt()
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(prk, HMAC_ALGORITHM))

        val output = ByteArrayOutputStream()
        var currentBlock = ByteArray(0)
        val effectiveInfo = info ?: ByteArray(0)

        for (i in 1..n) {
            mac.reset()
            mac.update(currentBlock)
            mac.update(effectiveInfo)
            mac.update(i.toByte())
            currentBlock = mac.doFinal()

            val bytesToWrite = Math.min(currentBlock.size, length - output.size())
            output.write(currentBlock, 0, bytesToWrite)
        }

        return output.toByteArray()
    }

    /**
     * Operación combinada Extract-and-Expand.
     */
    fun deriveKey(salt: ByteArray?, ikm: ByteArray, info: ByteArray?, length: Int): ByteArray {
        val prk = extract(salt, ikm)
        return expand(prk, info, length)
    }
}
