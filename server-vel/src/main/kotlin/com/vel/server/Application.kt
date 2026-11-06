package com.vel.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

// Almacén en memoria de las conexiones activas por su ID de dispositivo o sesión
private val activeConnections = ConcurrentHashMap<String, DefaultWebSocketSession>()

// Registro de persistencia mínima de autorizaciones y revocaciones
private val authorizations = ConcurrentHashMap<String, String>() // terminalId -> coreId
private val revocations = ConcurrentHashMap<String, Long>()       // terminalId -> revokedAt

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(30)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        routing {
            get("/health") {
                call.respondText(
                    """{"status":"UP","activeConnections":${activeConnections.size},"revocations":${revocations.size}}"""
                )
            }

            webSocket("/ws/{deviceId}") {
                val deviceId = call.parameters["deviceId"] ?: return@webSocket

                // Comprobar si el dispositivo se encuentra en la lista de revocaciones
                if (revocations.containsKey(deviceId)) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Dispositivo revocado por CORE"))
                    return@webSocket
                }

                activeConnections[deviceId] = this

                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val payload = frame.readText()
                            // El payload esperado incluye un destinatario y el mensaje cifrado: {"targetId": "...", "data": "..."}
                            // El servidor actúa como relevo ciego reenviando el paquete al destino correspondiente.
                            forwardPacket(deviceId, payload)
                        }
                    }
                } catch (e: ClosedReceiveChannelException) {
                    // Manejo de desconexión normal
                } catch (e: Exception) {
                    // Manejo de errores de conexión
                } finally {
                    activeConnections.remove(deviceId)
                }
            }
        }
    }.start(wait = true)
}

/**
 * Reenvía paquetes cifrados de forma ciega sin descifrar el contenido criptográfico (AES-GCM).
 * El servidor actúa estrictamente como relevo de transporte.
 */
suspend fun forwardPacket(senderId: String, payload: String) {
    try {
        val json = JSONObject(payload)
        val type = json.optString("type")

        // 1. Manejo de orden de revocación iniciada por CORE
        if (type == "REVOKE_DEVICE") {
            val targetId = json.optString("targetTerminalId").ifEmpty { json.optString("targetId") }
            if (targetId.isNotEmpty()) {
                revocations[targetId] = System.currentTimeMillis()
                authorizations.remove(targetId)

                // Si la terminal tiene una sesión abierta, cerrarla inmediatamente
                activeConnections[targetId]?.close(
                    CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Revocado por CORE")
                )
                activeConnections.remove(targetId)

                // Confirmación enviada de vuelta al CORE emisor
                val confirmResponse = JSONObject().apply {
                    put("type", "REVOCATION_CONFIRMED")
                    put("targetTerminalId", targetId)
                    put("timestamp", System.currentTimeMillis())
                }
                activeConnections[senderId]?.send(Frame.Text(confirmResponse.toString()))
            }
            return
        }

        // 2. Registro de nuevo emparejamiento autorizado
        if (type == "PAIRING_REGISTER") {
            val terminalId = json.optString("terminalId")
            if (terminalId.isNotEmpty()) {
                revocations.remove(terminalId)
                authorizations[terminalId] = senderId
                val registeredResponse = JSONObject().apply {
                    put("type", "PAIRING_REGISTERED")
                    put("terminalId", terminalId)
                    put("timestamp", System.currentTimeMillis())
                }
                activeConnections[senderId]?.send(Frame.Text(registeredResponse.toString()))
            }
            return
        }

        // 3. Enrutamiento ciego de paquetes cifrados (targetId o recipientId)
        val targetId = json.optString("targetId").ifEmpty { json.optString("recipientId") }
        if (targetId.isEmpty()) return

        // Validar que el remitente o el destinatario no estén revocados
        if (revocations.containsKey(senderId) || revocations.containsKey(targetId)) {
            val errorResponse = JSONObject().apply {
                put("type", "ERROR")
                put("error", "DEVICE_REVOKED")
                put("message", "Operación rechazada: uno de los dispositivos está revocado.")
            }
            activeConnections[senderId]?.send(Frame.Text(errorResponse.toString()))
            return
        }

        val destinationSession = activeConnections[targetId]
        if (destinationSession != null) {
            // Reenvío íntegro y opaco de la carga útil sin procesar claves ni contenido
            destinationSession.send(Frame.Text(payload))
        } else {
            // Notificación de que el destinatario no está conectado (sin encolar contenido privado)
            val offlineResponse = JSONObject().apply {
                put("type", "DELIVERY_FAILED")
                put("reason", "RECIPIENT_OFFLINE")
                put("operationId", json.optString("operationId"))
                put("recipientId", targetId)
            }
            activeConnections[senderId]?.send(Frame.Text(offlineResponse.toString()))
        }
    } catch (e: Exception) {
        // Ignorar paquetes malformados
    }
}
