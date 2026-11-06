package com.vel.app.pairing

import com.vel.app.ui.navigation.DeviceRole

/**
 * Estados del ciclo de emparejamiento entre VEL CORE y VEL TERMINAL.
 */
sealed class PairingState {
    object Unpaired : PairingState()
    data class PairingPending(val session: PairingSession) : PairingState()
    data class AwaitingConfirmation(
        val session: PairingSession,
        val sasCode: String,
        val peerFingerprint: String,
        val peerRole: DeviceRole
    ) : PairingState()
    data class VerifiedPaired(val device: PairedDevice) : PairingState()
    data class Rejected(val reason: String) : PairingState()
    data class Error(val message: String) : PairingState()
    object Unknown : PairingState()
}

/**
 * Sesión efímera de emparejamiento generada con tiempo de caducidad y protección contra repetición.
 */
data class PairingSession(
    val sessionId: String,
    val ephemeralCode: String,
    val originRole: DeviceRole,
    val initiatorDeviceId: String,
    val initiatorFingerprint: String,
    val initiatorPublicKeyBase64: String,
    val createdAt: Long,
    val expiresAt: Long,
    var isUsed: Boolean = false
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt
}

/**
 * Registro de confianza de un dispositivo emparejado y verificado criptográficamente.
 *
 * POLÍTICA DE SEGURIDAD CRIPTOGRÁFICA (VEL 0.1):
 * - Este modelo contiene exclusivamente metadatos públicos de identidad de confianza.
 * - NINGUNA clave criptográfica de sesión se almacena de forma persistente.
 * - La propiedad [sessionKeyBase64] se mantiene únicamente para compatibilidad hacia atrás
 *   en llamadas en memoria, pero su valor NUNCA se escribe en disco ni se persiste.
 */
data class PairedDevice(
    val id: String,
    val name: String,
    val role: DeviceRole,
    val fingerprint: String,
    val publicKeyBase64: String,
    val pairedAt: Long,
    val sasCode: String,
    val isLocallyRevoked: Boolean = false,
    val statusText: String = if (isLocallyRevoked) "Revocado localmente" else "Emparejado y verificado",
    @Deprecated("Las claves de sesión son estrictamente efímeras y NUNCA se persisten en almacenamiento. Solo existen en memoria.")
    val sessionKeyBase64: String? = null
) {
    val shortFingerprint: String
        get() = if (fingerprint.length >= 14) {
            "${fingerprint.take(8)}...${fingerprint.takeLast(5)}"
        } else {
            fingerprint
        }
}
