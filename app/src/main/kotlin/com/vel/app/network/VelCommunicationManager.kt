package com.vel.app.network

import com.vel.app.crypto.AesGcm
import com.vel.app.pairing.PairingRepository
import com.vel.app.security.DeviceIdentity
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestor de alto nivel para transferencias de mensajes cifrados de extremo a extremo,
 * confirmaciones autenticadas y revocación remota sobre el cliente Relay.
 */
class VelCommunicationManager(
    private val identity: DeviceIdentity,
    private val relayClient: VelRelayClient,
    private val repository: PairingRepository
) : VelRelayClient.Listener {

    interface CommunicationListener {
        fun onConnectionStateChanged(state: RelayConnectionState, details: String?)
        fun onTestMessageReceived(senderId: String, plaintext: String)
        fun onRevocationCompleted(targetTerminalId: String, isSuccess: Boolean)
        fun onPairingOfferRegistered(code: String) {}
        fun onPairingPeerReady(
            code: String,
            peerDeviceId: String,
            peerRole: String,
            peerFingerprint: String,
            sasCode: String,
            peerPublicKeyBase64: String
        ) {}
        fun onPairingError(error: String, message: String) {}
    }

    private var listener: CommunicationListener? = null
    private val pendingTestOperations = ConcurrentHashMap<String, (TestMessageExchangeResult) -> Unit>()
    private val operationStartTimes = ConcurrentHashMap<String, Long>()

    init {
        relayClient.setListener(this)
    }

    fun setListener(listener: CommunicationListener?) {
        this.listener = listener
    }

    /**
     * Registra la clave de sesión simétrica en la autoridad única (PairingRepository).
     */
    fun registerSessionKey(peerDeviceId: String, sessionKey: ByteArray) {
        repository.setInMemorySessionKey(peerDeviceId, sessionKey)
    }

    /**
     * Obtiene una copia defensiva de la clave de sesión desde la autoridad única.
     */
    fun getSessionKey(deviceId: String): ByteArray? {
        return repository.getSessionKeyForDevice(deviceId)
    }

    /**
     * Purga la clave de sesión en la autoridad única.
     */
    fun clearSessionKey(deviceId: String) {
        repository.clearSessionKey(deviceId)
    }

    fun sendPairingOffer(code: String, expiresAt: Long): Boolean {
        return relayClient.sendPairingOffer(code, expiresAt)
    }

    fun sendPairingClaim(code: String): Boolean {
        return relayClient.sendPairingClaim(code)
    }

    fun connect() = relayClient.connect()
    fun disconnect() = relayClient.disconnect()
    fun getConnectionState(): RelayConnectionState = relayClient.getState()
    fun getRelayUrl(): String = relayClient.getRelayUrl()
    fun updateRelayUrl(newUrl: String) = relayClient.updateRelayUrl(newUrl)

    /**
     * Cifra y transmite un mensaje de prueba a un dispositivo emparejado.
     */
    fun sendEncryptedTestMessage(
        recipientId: String,
        sessionKey: ByteArray,
        plaintext: String,
        onResult: (TestMessageExchangeResult) -> Unit
    ) {
        repository.setInMemorySessionKey(recipientId, sessionKey)

        val operationId = UUID.randomUUID().toString()
        val plainBytes = plaintext.toByteArray(Charsets.UTF_8)

        // Cifrado AES-256-GCM con IV de 96 bits
        val encrypted = AesGcm.encrypt(sessionKey, plainBytes)
        plainBytes.fill(0) // Limpieza inmediata de texto plano intermedio
        val ivBase64 = Base64.getEncoder().encodeToString(encrypted.iv)
        val ciphertextBase64 = Base64.getEncoder().encodeToString(encrypted.ciphertextWithTag)

        val packet = WirePacket(
            type = "ENCRYPTED_MESSAGE",
            senderId = identity.deviceId,
            recipientId = recipientId,
            operationId = operationId,
            iv = ivBase64,
            ciphertext = ciphertextBase64,
            timestamp = System.currentTimeMillis()
        )

        pendingTestOperations[operationId] = onResult
        operationStartTimes[operationId] = System.currentTimeMillis()

        val sent = relayClient.sendPacket(packet)
        if (!sent) {
            pendingTestOperations.remove(operationId)
            operationStartTimes.remove(operationId)
            onResult(
                TestMessageExchangeResult(
                    operationId = operationId,
                    plaintext = plaintext,
                    isConfirmed = false,
                    roundTripTimeMs = 0,
                    errorMessage = "No hay conexión activa con el servidor de relevo. Mensaje descartado."
                )
            )
        }
    }

    /**
     * Envía una solicitud de revocación remota de un Terminal iniciada desde CORE.
     */
    fun requestRemoteRevocation(
        targetTerminalId: String,
        reason: String = "Revocado por CORE"
    ) {
        val packet = WirePacket(
            type = "REVOKE_DEVICE",
            senderId = identity.deviceId,
            targetTerminalId = targetTerminalId,
            reason = reason,
            timestamp = System.currentTimeMillis()
        )
        relayClient.sendPacket(packet)
    }

    // --- Implementación de VelRelayClient.Listener ---

    override fun onStateChanged(state: RelayConnectionState, details: String?) {
        listener?.onConnectionStateChanged(state, details)
    }

    override fun onEncryptedPacketReceived(
        senderId: String,
        operationId: String,
        ivBase64: String,
        ciphertextBase64: String
    ) {
        val sessionKey = repository.getSessionKeyForDevice(senderId) ?: return

        try {
            val iv = Base64.getDecoder().decode(ivBase64)
            val ciphertext = Base64.getDecoder().decode(ciphertextBase64)

            // Descifrado y validación del tag de 128 bits
            val decryptedBytes = AesGcm.decrypt(sessionKey, iv, ciphertext)
            val plaintext = String(decryptedBytes, Charsets.UTF_8)
            decryptedBytes.fill(0) // Limpieza inmediata del buffer descifrado

            // Responder confirmación autenticada al remitente a través del relay
            val confirmPacket = WirePacket(
                type = "CONFIRMATION",
                senderId = identity.deviceId,
                recipientId = senderId,
                operationId = operationId,
                status = "CONFIRMED",
                timestamp = System.currentTimeMillis()
            )
            relayClient.sendPacket(confirmPacket)

            listener?.onTestMessageReceived(senderId, plaintext)
        } catch (e: Exception) {
            // Alteración detectada o clave no coincide
        } finally {
            sessionKey.fill(0) // Limpieza de la copia defensiva
        }
    }

    override fun onOperationConfirmed(operationId: String, status: String) {
        val callback = pendingTestOperations.remove(operationId)
        val startTime = operationStartTimes.remove(operationId) ?: System.currentTimeMillis()
        val elapsed = System.currentTimeMillis() - startTime

        callback?.invoke(
            TestMessageExchangeResult(
                operationId = operationId,
                plaintext = "Mensaje verificado",
                isConfirmed = status == "CONFIRMED",
                roundTripTimeMs = elapsed
            )
        )
    }

    override fun onRevocationConfirmed(targetTerminalId: String) {
        // Confirmación recibida del servidor: revocar y destruir clave en la autoridad única
        repository.revokeDeviceLocally(targetTerminalId)
        listener?.onRevocationCompleted(targetTerminalId, true)
    }

    override fun onDeliveryFailed(operationId: String, reason: String) {
        val callback = pendingTestOperations.remove(operationId)
        operationStartTimes.remove(operationId)
        callback?.invoke(
            TestMessageExchangeResult(
                operationId = operationId,
                plaintext = "",
                isConfirmed = false,
                roundTripTimeMs = 0,
                errorMessage = "Fallo de entrega en el Relay: $reason"
            )
        )
    }

    override fun onPairingOfferRegistered(code: String) {
        listener?.onPairingOfferRegistered(code)
    }

    override fun onPairingOfferFound(
        code: String,
        peerDeviceId: String,
        peerRole: String,
        peerFingerprint: String,
        peerPublicKeyBase64: String
    ) {
        var sharedSecret: ByteArray? = null
        var sessionKey: ByteArray? = null
        try {
            val peerPubKey = com.vel.app.crypto.KeyAgreementManager.decodePublicKey(peerPublicKeyBase64)
            val privKey = com.vel.app.security.IdentityManager.getInstance().getPrivateKey()
            sharedSecret = com.vel.app.crypto.KeyAgreementManager.computeSharedSecret(privKey, peerPubKey)
            val sasCode = com.vel.app.crypto.KeyAgreementManager.deriveShortAuthenticationString(sharedSecret)
            sessionKey = com.vel.app.crypto.KeyAgreementManager.deriveSessionKey(sharedSecret)
            repository.setInMemorySessionKey(peerDeviceId, sessionKey)

            listener?.onPairingPeerReady(code, peerDeviceId, peerRole, peerFingerprint, sasCode, peerPublicKeyBase64)
        } catch (e: Exception) {
            listener?.onPairingError("CRYPTO_ERROR", e.message ?: "Fallo al calcular acuerdo de claves")
        } finally {
            sharedSecret?.fill(0)
            sessionKey?.fill(0)
        }
    }

    override fun onPairingClaimReceived(
        code: String,
        peerDeviceId: String,
        peerRole: String,
        peerFingerprint: String,
        peerPublicKeyBase64: String
    ) {
        var sharedSecret: ByteArray? = null
        var sessionKey: ByteArray? = null
        try {
            val peerPubKey = com.vel.app.crypto.KeyAgreementManager.decodePublicKey(peerPublicKeyBase64)
            val privKey = com.vel.app.security.IdentityManager.getInstance().getPrivateKey()
            sharedSecret = com.vel.app.crypto.KeyAgreementManager.computeSharedSecret(privKey, peerPubKey)
            val sasCode = com.vel.app.crypto.KeyAgreementManager.deriveShortAuthenticationString(sharedSecret)
            sessionKey = com.vel.app.crypto.KeyAgreementManager.deriveSessionKey(sharedSecret)
            repository.setInMemorySessionKey(peerDeviceId, sessionKey)

            listener?.onPairingPeerReady(code, peerDeviceId, peerRole, peerFingerprint, sasCode, peerPublicKeyBase64)
        } catch (e: Exception) {
            listener?.onPairingError("CRYPTO_ERROR", e.message ?: "Fallo al calcular acuerdo de claves")
        } finally {
            sharedSecret?.fill(0)
            sessionKey?.fill(0)
        }
    }

    override fun onPairingError(error: String, message: String) {
        listener?.onPairingError(error, message)
    }
}
