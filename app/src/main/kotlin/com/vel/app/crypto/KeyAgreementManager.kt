package com.vel.app.crypto

import java.nio.ByteBuffer
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement

/**
 * Gestor de acuerdo de claves mediante ECDH (Elliptic Curve Diffie-Hellman) con curva estándar NIST P-256 (secp256r1).
 * Implementa la derivación de clave de sesión con HKDF-SHA-256 y el cálculo del código
 * de autenticación visual corto (SAS - Short Authentication String de 6 dígitos).
 */
object KeyAgreementManager {

    const val CURVE_NAME = "secp256r1"
    private const val KEY_AGREEMENT_ALGO = "ECDH"
    private const val KEY_FACTORY_ALGO = "EC"
    private const val DIGEST_ALGO = "SHA-256"

    /**
     * Genera un par de claves en curva elíptica P-256.
     */
    fun generateEcP256KeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(KEY_FACTORY_ALGO)
        keyPairGenerator.initialize(ECGenParameterSpec(CURVE_NAME), SecureRandom())
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Calcula la huella digital SHA-256 de una clave pública en formato legible (hexadecimal).
     */
    fun computeFingerprint(publicKey: PublicKey): String {
        return computeFingerprint(publicKey.encoded)
    }

    /**
     * Calcula la huella digital SHA-256 de bytes codificados en formato legible.
     */
    fun computeFingerprint(publicKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance(DIGEST_ALGO).digest(publicKeyBytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    /**
     * Decodifica una clave pública en formato X.509 a partir de Base64.
     */
    fun decodePublicKey(base64PublicKey: String): PublicKey {
        val keyBytes = Base64.getDecoder().decode(base64PublicKey)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGO)
        return keyFactory.generatePublic(spec)
    }

    /**
     * Codifica una clave pública en formato Base64 estándar.
     */
    fun encodePublicKey(publicKey: PublicKey): String {
        return Base64.getEncoder().encodeToString(publicKey.encoded)
    }

    /**
     * Ejecuta el acuerdo de claves Diffie-Hellman en curva elíptica (ECDH)
     * entre una clave privada local y la clave pública del par remoto.
     * @return El secreto compartido en bruto.
     */
    fun computeSharedSecret(privateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGO)
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        return keyAgreement.generateSecret()
    }

    /**
     * Deriva una clave de sesión AES-256 a partir del secreto compartido utilizando HKDF-SHA-256.
     */
    fun deriveSessionKey(
        sharedSecret: ByteArray,
        salt: ByteArray? = null,
        info: String = "VEL-SESSION-KEY-v1"
    ): ByteArray {
        return Hkdf.deriveKey(
            salt = salt,
            ikm = sharedSecret,
            info = info.toByteArray(Charsets.UTF_8),
            length = AesGcm.KEY_SIZE_BYTES
        )
    }

    /**
     * Deriva un código numérico corto de 6 dígitos (SAS - Short Authentication String)
     * para que los usuarios puedan contrastar visualmente en las pantallas de ambos teléfonos
     * que ambos derivaron exactamente el mismo secreto compartido sin ataque de hombre en el medio (MITM).
     */
    fun deriveShortAuthenticationString(
        sharedSecret: ByteArray,
        info: String = "VEL-SAS-CODE-v1"
    ): String {
        val sasBytes = Hkdf.deriveKey(
            salt = null,
            ikm = sharedSecret,
            info = info.toByteArray(Charsets.UTF_8),
            length = 4
        )
        val number = Math.abs(ByteBuffer.wrap(sasBytes).int) % 1_000_000
        return "%06d".format(number)
    }
}
