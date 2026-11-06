package com.vel.app.network

import java.util.UUID

/**
 * Estados detallados de la conexión con el servidor de relevo (Relay).
 */
enum class RelayConnectionState {
    DISCONNECTED,        // Sin conexión
    CONNECTING,          // Conectando al servidor
    CONNECTED,           // WebSocket abierto, esperando autenticación
    AUTHENTICATING,      // Transmitiendo credenciales de dispositivo
    AUTHENTICATED,       // Conexión autorizada y lista para transferencias
    REVOKED,             // Conexión rechazada: el dispositivo ha sido revocado
    ERROR                // Error de red o rechazo de conexión
}

/**
 * Paquete de transporte serializado para la transmisión por WebSocket.
 * El servidor Relay solo inspecciona el remitente y destinatario para enrutar;
 * el contenido confidencial (ciphertext e iv) permanece cifrado de extremo a extremo.
 */
data class WirePacket(
    val type: String,
    val senderId: String,
    val recipientId: String? = null,
    val sessionId: String? = null,
    val operationId: String = UUID.randomUUID().toString(),
    val iv: String? = null,
    val ciphertext: String? = null,
    val status: String? = null,
    val role: String? = null,
    val fingerprint: String? = null,
    val targetTerminalId: String? = null,
    val reason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Resultado verificado del intercambio de un mensaje de prueba cifrado.
 */
data class TestMessageExchangeResult(
    val operationId: String,
    val plaintext: String,
    val isConfirmed: Boolean,
    val roundTripTimeMs: Long,
    val errorMessage: String? = null
)
