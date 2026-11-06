package com.vel.app.subscription

import org.json.JSONObject

/**
 * Representa un dispositivo vinculado autorizado dentro del plan de suscripción de VEL.
 */
data class LinkedDeviceSubscription(
    val deviceId: String,
    val role: String,
    val status: String
) {
    val isActive: Boolean
        get() = status.equals("active", ignoreCase = true)
}

/**
 * Modelo de datos del perfil de suscripción y licencia de usuario en VEL.
 */
data class UserSubscriptionProfile(
    val email: String,
    val subscriptionStatus: String,
    val expiresAt: String,
    val linkedDevices: Map<String, LinkedDeviceSubscription>
) {
    val isActive: Boolean
        get() = subscriptionStatus.equals("active", ignoreCase = true)

    companion object {
        const val DEFAULT_JSON = """{
  "email": "usuario@example.com",
  "subscriptionStatus": "active",
  "expiresAt": "2027-03-01T00:00:00Z",
  "linkedDevices": {
    "core_device_id_xyz": { "role": "CORE", "status": "active" },
    "terminal_device_id_abc": { "role": "TERMINAL", "status": "active" }
  }
}"""

        fun fromJson(jsonStr: String): UserSubscriptionProfile {
            val obj = JSONObject(jsonStr)
            val email = obj.optString("email", "usuario@example.com")
            val status = obj.optString("subscriptionStatus", "active")
            val expiresAt = obj.optString("expiresAt", "2027-03-01T00:00:00Z")

            val devicesMap = mutableMapOf<String, LinkedDeviceSubscription>()
            val linkedObj = obj.optJSONObject("linkedDevices")
            if (linkedObj != null) {
                val keys = linkedObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val dev = linkedObj.getJSONObject(key)
                    devicesMap[key] = LinkedDeviceSubscription(
                        deviceId = key,
                        role = dev.optString("role", "TERMINAL"),
                        status = dev.optString("status", "active")
                    )
                }
            }

            return UserSubscriptionProfile(
                email = email,
                subscriptionStatus = status,
                expiresAt = expiresAt,
                linkedDevices = devicesMap
            )
        }
    }
}
