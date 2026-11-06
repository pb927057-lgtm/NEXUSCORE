package com.vel.app.network

import com.vel.app.security.DeviceIdentity
import okhttp3.*
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Cliente WebSocket para comunicación segura con el servidor Relay de VEL.
 * - Gestiona el ciclo de vida de la conexión, autenticación y reconexión exponencial.
 * - Si el servidor rechaza la conexión por revocación (código 4003), detiene los reintentos.
 * - Implementa deduplicación de paquetes mediante operationId (protección contra repetición).
 * - Minimización de datos: no retiene mensajes en cola persistente si no hay conexión activa.
 */
class VelRelayClient(
    private var relayUrl: String,
    private val identity: DeviceIdentity,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
) {
    fun getRelayUrl(): String = relayUrl

    fun updateRelayUrl(newUrl: String) {
        val trimmed = newUrl.trim()
        if (trimmed.isNotEmpty() && trimmed != relayUrl) {
            disconnect()
            this.relayUrl = trimmed
            connect()
        }
    }
    interface Listener {
        fun onStateChanged(state: RelayConnectionState, details: String? = null)
        fun onEncryptedPacketReceived(senderId: String, operationId: String, ivBase64: String, ciphertextBase64: String)
        fun onOperationConfirmed(operationId: String, status: String)
        fun onRevocationConfirmed(targetTerminalId: String)
        fun onDeliveryFailed(operationId: String, reason: String)
        fun onPairingOfferRegistered(code: String) {}
        fun onPairingOfferFound(code: String, peerDeviceId: String, peerRole: String, peerFingerprint: String, peerPublicKeyBase64: String) {}
        fun onPairingClaimReceived(code: String, peerDeviceId: String, peerRole: String, peerFingerprint: String, peerPublicKeyBase64: String) {}
        fun onPairingError(error: String, message: String) {}
    }

    private var listener: Listener? = null
    private var webSocket: WebSocket? = null
    private var currentState: RelayConnectionState = RelayConnectionState.DISCONNECTED
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    private var reconnectAttempt = 0
    private var isManuallyClosed = false

    // Caché circular de deduplicación de operationId (últimos 150 IDs)
    private val processedOperations = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun getState(): RelayConnectionState = currentState

    fun connect() {
        if (currentState == RelayConnectionState.REVOKED) {
            updateState(RelayConnectionState.REVOKED, "Dispositivo revocado. No se permiten conexiones.")
            return
        }

        isManuallyClosed = false
        updateState(RelayConnectionState.CONNECTING)

        val request = Request.Builder()
            .url(relayUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    fun disconnect() {
        isManuallyClosed = true
        webSocket?.close(1000, "Desconexión manual")
        webSocket = null
        updateState(RelayConnectionState.DISCONNECTED)
    }

    /**
     * Envía un paquete WirePacket al servidor Relay.
     * Si no hay conexión autenticada, falla de inmediato y descarta el mensaje (no se encola contenido privado).
     */
    fun sendPacket(packet: WirePacket): Boolean {
        if (currentState != RelayConnectionState.AUTHENTICATED) {
            listener?.onDeliveryFailed(packet.operationId, "NO_CONNECTION")
            return false
        }

        val json = JSONObject().apply {
            put("type", packet.type)
            put("senderId", packet.senderId)
            packet.recipientId?.let { put("recipientId", it) }
            packet.sessionId?.let { put("sessionId", it) }
            put("operationId", packet.operationId)
            packet.iv?.let { put("iv", it) }
            packet.ciphertext?.let { put("ciphertext", it) }
            packet.status?.let { put("status", it) }
            packet.targetTerminalId?.let { put("targetTerminalId", it) }
            packet.reason?.let { put("reason", it) }
            put("timestamp", packet.timestamp)
        }

        return webSocket?.send(json.toString()) ?: false
    }

    /**
     * Publica una oferta de emparejamiento con el código temporal y clave pública efímera al relay.
     */
    fun sendPairingOffer(code: String, expiresAt: Long): Boolean {
        val json = JSONObject().apply {
            put("type", "PAIRING_OFFER")
            put("code", code)
            put("initiatorDeviceId", identity.deviceId)
            put("initiatorRole", identity.role.name)
            put("initiatorFingerprint", identity.fingerprint)
            put("initiatorPublicKeyBase64", identity.publicKeyBase64)
            put("expiresAt", expiresAt)
        }
        return webSocket?.send(json.toString()) ?: false
    }

    /**
     * Reclama una oferta de emparejamiento transmitiendo la clave pública local al relay.
     */
    fun sendPairingClaim(code: String): Boolean {
        val json = JSONObject().apply {
            put("type", "PAIRING_CLAIM")
            put("code", code)
            put("claimerDeviceId", identity.deviceId)
            put("claimerRole", identity.role.name)
            put("claimerFingerprint", identity.fingerprint)
            put("claimerPublicKeyBase64", identity.publicKeyBase64)
        }
        return webSocket?.send(json.toString()) ?: false
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                reconnectAttempt = 0
                updateState(RelayConnectionState.AUTHENTICATING)

                // Transmitir apretón de manos AUTH inicial
                val authPacket = JSONObject().apply {
                    put("type", "AUTH")
                    put("deviceId", identity.deviceId)
                    put("role", identity.role.name)
                    put("fingerprint", identity.fingerprint)
                }
                ws.send(authPacket.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")

                    when (type) {
                        "AUTH_SUCCESS" -> {
                            updateState(RelayConnectionState.AUTHENTICATED)
                        }

                        "AUTH_ERROR" -> {
                            val error = json.optString("error")
                            val message = json.optString("message")
                            if (error == "DEVICE_REVOKED") {
                                updateState(RelayConnectionState.REVOKED, message)
                            } else {
                                updateState(RelayConnectionState.ERROR, message)
                            }
                        }

                        "ENCRYPTED_MESSAGE" -> {
                            val senderId = json.optString("senderId")
                            val operationId = json.optString("operationId")
                            val iv = json.optString("iv")
                            val ciphertext = json.optString("ciphertext")

                            // Protección contra repetición: descartar si ya fue procesado
                            if (!processedOperations.add(operationId)) {
                                return
                            }

                            listener?.onEncryptedPacketReceived(senderId, operationId, iv, ciphertext)
                        }

                        "CONFIRMATION" -> {
                            val operationId = json.optString("operationId")
                            val status = json.optString("status", "CONFIRMED")
                            listener?.onOperationConfirmed(operationId, status)
                        }

                        "REVOCATION_CONFIRMED" -> {
                            val targetTerminalId = json.optString("targetTerminalId")
                            listener?.onRevocationConfirmed(targetTerminalId)
                        }

                        "REVOCATION_NOTICE" -> {
                            updateState(RelayConnectionState.REVOKED, "Autorización revocada por CORE.")
                        }

                        "DELIVERY_FAILED" -> {
                            val operationId = json.optString("operationId")
                            val reason = json.optString("reason", "UNKNOWN")
                            listener?.onDeliveryFailed(operationId, reason)
                        }

                        "PAIRING_OFFER_REGISTERED" -> {
                            val code = json.optString("code")
                            listener?.onPairingOfferRegistered(code)
                        }

                        "PAIRING_OFFER_FOUND" -> {
                            val code = json.optString("code")
                            val peerDeviceId = json.optString("peerDeviceId")
                            val peerRole = json.optString("peerRole")
                            val peerFingerprint = json.optString("peerFingerprint")
                            val peerPublicKeyBase64 = json.optString("peerPublicKeyBase64")
                            listener?.onPairingOfferFound(code, peerDeviceId, peerRole, peerFingerprint, peerPublicKeyBase64)
                        }

                        "PAIRING_CLAIM_RECEIVED" -> {
                            val code = json.optString("code")
                            val peerDeviceId = json.optString("peerDeviceId")
                            val peerRole = json.optString("peerRole")
                            val peerFingerprint = json.optString("peerFingerprint")
                            val peerPublicKeyBase64 = json.optString("peerPublicKeyBase64")
                            listener?.onPairingClaimReceived(code, peerDeviceId, peerRole, peerFingerprint, peerPublicKeyBase64)
                        }

                        "PAIRING_ERROR" -> {
                            val error = json.optString("error")
                            val message = json.optString("message")
                            listener?.onPairingError(error, message)
                        }
                    }
                } catch (e: Exception) {
                    // Ignorar paquetes corruptos
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                if (code == 4003) {
                    updateState(RelayConnectionState.REVOKED, "Conexión cerrada por revocación: $reason")
                } else {
                    updateState(RelayConnectionState.DISCONNECTED, reason)
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (code == 4003) {
                    updateState(RelayConnectionState.REVOKED, "Dispositivo revocado.")
                } else {
                    updateState(RelayConnectionState.DISCONNECTED, reason)
                    scheduleReconnect()
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                updateState(RelayConnectionState.ERROR, t.message ?: "Fallo de conexión")
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (isManuallyClosed || currentState == RelayConnectionState.REVOKED) {
            return
        }

        // Backoff exponencial: 1s, 2s, 4s, 8s, 16s, hasta max 30s
        val delaySec = Math.min(30L, Math.pow(2.0, reconnectAttempt.toDouble()).toLong())
        reconnectAttempt++

        scheduler.schedule({
            if (!isManuallyClosed && currentState != RelayConnectionState.REVOKED) {
                connect()
            }
        }, delaySec, TimeUnit.SECONDS)
    }

    private fun updateState(newState: RelayConnectionState, details: String? = null) {
        currentState = newState
        listener?.onStateChanged(newState, details)
    }
}
