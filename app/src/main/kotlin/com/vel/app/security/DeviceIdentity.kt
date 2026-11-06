package com.vel.app.security

import com.vel.app.ui.navigation.DeviceRole

/**
 * Modelo inmutable que representa la identidad criptográfica persistente de esta instalación de VEL.
 * Ningún dato confidencial (como la clave privada) se expone fuera de Android Keystore / memoria segura.
 */
data class DeviceIdentity(
    val deviceId: String,
    val role: DeviceRole,
    val publicKeyBase64: String,
    val fingerprint: String,
    val createdAt: Long,
    val isHardwareBacked: Boolean
) {
    /**
     * Versión abreviada de la huella digital para visualización cómoda en la interfaz de usuario.
     */
    val shortFingerprint: String
        get() = if (fingerprint.length >= 14) {
            "${fingerprint.take(8)}...${fingerprint.takeLast(5)}"
        } else {
            fingerprint
        }
}
