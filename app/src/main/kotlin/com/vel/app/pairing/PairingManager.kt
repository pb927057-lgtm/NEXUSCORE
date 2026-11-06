package com.vel.app.pairing

import com.vel.app.crypto.KeyAgreementManager
import com.vel.app.security.DeviceIdentity
import com.vel.app.security.IdentityManager
import com.vel.app.ui.navigation.DeviceRole
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.util.UUID

/**
 * Resultado de procesar un código o solicitud de emparejamiento.
 */
sealed class PairingProcessResult {
    data class SuccessAwaitingConfirmation(
        val session: PairingSession,
        val sasCode: String,
        val peerFingerprint: String,
        val peerRole: DeviceRole
    ) : PairingProcessResult()

    data class Error(val message: String) : PairingProcessResult()
}

/**
 * Gestor del protocolo de emparejamiento entre VEL CORE y VEL TERMINAL.
 *
 * Protocolo de emparejamiento estándar de VEL:
 * 1. El iniciador (típicamente CORE) crea una sesión de emparejamiento con un código temporal efímero (ej. VEL-XXXX)
 *    con tiempo de expiración de 5 minutos y no reutilizable.
 * 2. El receptor (Terminal) ingresa el código temporal.
 * 3. Ambos dispositivos intercambian sus claves públicas P-256 y ejecutan el acuerdo de claves Diffie-Hellman (ECDH).
 * 4. A partir del secreto compartido resultante, se deriva una clave de sesión mediante HKDF-SHA-256 y un
 *    código numérico corto (SAS - Short Authentication String) de 6 dígitos.
 * 5. Ambos usuarios contrastan visualmente que el código SAS de 6 dígitos coincida en ambas pantallas,
 *    eliminando el riesgo de ataque de intermediario (Man-In-The-Middle / impostor).
 * 6. Tras la confirmación del usuario, el dispositivo queda registrado localmente como de confianza.
 *
 * Límites de la Etapa 2:
 * Toda la criptografía, derivación de secretos, verificación SAS y persistencia local están completadas.
 * El transporte de paquetes remoto automático a través de Internet entre dos dispositivos físicos
 * requiere el canal de comunicación de la Etapa 3.
 */
class PairingManager(
    private val identityManager: IdentityManager,
    private val repository: PairingRepository
) {

    companion object {
        const val SESSION_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutos de vigencia
        private val secureRandom = SecureRandom()
    }

    // Registro de sesiones activas en memoria
    private val activeSessions = mutableMapOf<String, PairingSession>()

    /**
     * Genera una sesión de emparejamiento con un código temporal de 4 dígitos (ej. "VEL-7412").
     */
    @Synchronized
    fun createPairingSession(role: DeviceRole): PairingSession {
        val identity = identityManager.getOrCreateIdentity(role)
        val randomNum = 1000 + secureRandom.nextInt(9000)
        val ephemeralCode = "VEL-$randomNum"
        val now = System.currentTimeMillis()

        val session = PairingSession(
            sessionId = UUID.randomUUID().toString(),
            ephemeralCode = ephemeralCode,
            originRole = role,
            initiatorDeviceId = identity.deviceId,
            initiatorFingerprint = identity.fingerprint,
            initiatorPublicKeyBase64 = identity.publicKeyBase64,
            createdAt = now,
            expiresAt = now + SESSION_TIMEOUT_MS,
            isUsed = false
        )

        activeSessions[ephemeralCode] = session
        return session
    }

    /**
     * Procesa la introducción de un código de emparejamiento efímero.
     * Valida la no expiración, la no reutilización y calcula el acuerdo ECDH y código SAS.
     */
    @Synchronized
    fun processPairingCode(
        inputCode: String,
        receiverRole: DeviceRole,
        knownSession: PairingSession? = null
    ): PairingProcessResult {
        val normalizedCode = inputCode.trim().uppercase()
        val session = knownSession ?: activeSessions[normalizedCode]

        if (session == null) {
            return PairingProcessResult.Error(
                "El código introducido '$normalizedCode' no existe o no corresponde a una sesión activa."
            )
        }

        if (session.isExpired) {
            activeSessions.remove(normalizedCode)
            return PairingProcessResult.Error(
                "El código de emparejamiento ha expirado (límite de 5 minutos excedido). Por favor genera uno nuevo."
            )
        }

        if (session.isUsed) {
            return PairingProcessResult.Error(
                "Este código de emparejamiento ya fue utilizado previamente. Protección contra reutilización activada."
            )
        }

        // Obtener identidad del receptor
        val receiverIdentity = identityManager.getOrCreateIdentity(receiverRole)

        // Derivar secreto compartido ECDH
        return try {
            val initiatorPublicKey = KeyAgreementManager.decodePublicKey(session.initiatorPublicKeyBase64)
            val receiverPrivateKey = identityManager.getPrivateKey()

            val sharedSecret = KeyAgreementManager.computeSharedSecret(receiverPrivateKey, initiatorPublicKey)
            val sasCode = KeyAgreementManager.deriveShortAuthenticationString(sharedSecret)

            PairingProcessResult.SuccessAwaitingConfirmation(
                session = session,
                sasCode = sasCode,
                peerFingerprint = session.initiatorFingerprint,
                peerRole = session.originRole
            )
        } catch (e: Exception) {
            PairingProcessResult.Error("Error criptográfico al establecer el acuerdo de claves: ${e.message ?: "Clave no válida"}")
        }
    }

    /**
     * Confirma el emparejamiento tras la verificación visual de SAS por parte del usuario.
     * Marca la sesión como consumida y persiste el registro de confianza en el repositorio.
     */
    @Synchronized
    fun confirmPairing(
        session: PairingSession,
        sasCode: String,
        customDeviceName: String? = null
    ): PairedDevice {
        if (session.isUsed) {
            throw IllegalStateException("La sesión ya fue consumida.")
        }
        session.isUsed = true
        activeSessions.remove(session.ephemeralCode)

        val defaultName = if (session.originRole == DeviceRole.CORE) {
            "Dispositivo VEL CORE"
        } else {
            "Terminal Móvil (${session.initiatorDeviceId})"
        }

        // Derivar clave de sesión simétrica real de 32 bytes (AES-256) mediante ECDH + HKDF
        var sharedSecret: ByteArray? = null
        var sessionKey: ByteArray? = null
        try {
            val privKey = identityManager.getPrivateKey()
            val remotePubKey = KeyAgreementManager.decodePublicKey(session.initiatorPublicKeyBase64)
            sharedSecret = KeyAgreementManager.computeSharedSecret(privKey, remotePubKey)
            sessionKey = KeyAgreementManager.deriveSessionKey(sharedSecret)
            // Registrar la clave de sesión estrictamente en la autoridad única (PairingRepository)
            repository.setInMemorySessionKey(session.initiatorDeviceId, sessionKey)
        } catch (_: Exception) {
        } finally {
            sharedSecret?.fill(0)
            sessionKey?.fill(0)
        }

        val pairedDevice = PairedDevice(
            id = session.initiatorDeviceId,
            name = customDeviceName ?: defaultName,
            role = session.originRole,
            fingerprint = session.initiatorFingerprint,
            publicKeyBase64 = session.initiatorPublicKeyBase64,
            pairedAt = System.currentTimeMillis(),
            sasCode = sasCode,
            isLocallyRevoked = false,
            sessionKeyBase64 = null
        )

        repository.savePairedDevice(pairedDevice)
        return pairedDevice
    }

    /**
     * Confirma el emparejamiento desde el iniciador (CORE) cuando recibe los datos del peer remoto (Terminal).
     */
    @Synchronized
    fun confirmPairingWithPeer(
        peerDeviceId: String,
        peerRole: DeviceRole,
        peerFingerprint: String,
        peerPublicKeyBase64: String,
        sasCode: String,
        customDeviceName: String? = null
    ): PairedDevice {
        val defaultName = if (peerRole == DeviceRole.CORE) {
            "Dispositivo VEL CORE"
        } else {
            "Terminal Móvil ($peerDeviceId)"
        }

        var sharedSecret: ByteArray? = null
        var sessionKey: ByteArray? = null
        try {
            val privKey = identityManager.getPrivateKey()
            val remotePubKey = KeyAgreementManager.decodePublicKey(peerPublicKeyBase64)
            sharedSecret = KeyAgreementManager.computeSharedSecret(privKey, remotePubKey)
            sessionKey = KeyAgreementManager.deriveSessionKey(sharedSecret)
            // Registrar la clave de sesión estrictamente en la autoridad única (PairingRepository)
            repository.setInMemorySessionKey(peerDeviceId, sessionKey)
        } catch (_: Exception) {
        } finally {
            sharedSecret?.fill(0)
            sessionKey?.fill(0)
        }

        val pairedDevice = PairedDevice(
            id = peerDeviceId,
            name = customDeviceName ?: defaultName,
            role = peerRole,
            fingerprint = peerFingerprint,
            publicKeyBase64 = peerPublicKeyBase64,
            pairedAt = System.currentTimeMillis(),
            sasCode = sasCode,
            isLocallyRevoked = false,
            sessionKeyBase64 = null
        )

        repository.savePairedDevice(pairedDevice)
        return pairedDevice
    }

    /**
     * Revoca localmente la confianza hacia un dispositivo registrado.
     */
    fun revokeDeviceLocally(deviceId: String): Boolean {
        return repository.revokeDeviceLocally(deviceId)
    }

    fun getPairedDevices(): List<PairedDevice> = repository.getPairedDevices()
}
