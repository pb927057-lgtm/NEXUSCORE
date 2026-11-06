package com.vel.app

import com.vel.app.subscription.LinkedDeviceSubscription
import com.vel.app.subscription.UserSubscriptionProfile
import org.junit.Assert.*
import org.junit.Test

class SubscriptionModelTest {

    private val sampleJson = """
        {
          "email": "usuario@example.com",
          "subscriptionStatus": "active",
          "expiresAt": "2027-03-01T00:00:00Z",
          "linkedDevices": {
            "core_device_id_xyz": { "role": "CORE", "status": "active" },
            "terminal_device_id_abc": { "role": "TERMINAL", "status": "active" }
          }
        }
    """.trimIndent()

    @Test
    fun testParseSubscriptionProfileFromJson() {
        val profile = UserSubscriptionProfile.fromJson(sampleJson)

        assertEquals("usuario@example.com", profile.email)
        assertEquals("active", profile.subscriptionStatus)
        assertEquals("2027-03-01T00:00:00Z", profile.expiresAt)
        assertTrue("Suscripción debe estar activa", profile.isActive)

        assertEquals(2, profile.linkedDevices.size)

        val coreDevice = profile.linkedDevices["core_device_id_xyz"]
        assertNotNull(coreDevice)
        assertEquals("CORE", coreDevice?.role)
        assertEquals("active", coreDevice?.status)

        val terminalDevice = profile.linkedDevices["terminal_device_id_abc"]
        assertNotNull(terminalDevice)
        assertEquals("TERMINAL", terminalDevice?.role)
        assertEquals("active", terminalDevice?.status)
    }

    @Test
    fun testDeviceAuthorizationCheck() {
        val profile = UserSubscriptionProfile.fromJson(sampleJson)

        assertTrue(profile.isDeviceAuthorized("core_device_id_xyz"))
        assertTrue(profile.isDeviceAuthorized("terminal_device_id_abc"))
        assertFalse(profile.isDeviceAuthorized("unregistered_device_999"))
    }

    @Test
    fun testSerializationRoundTrip() {
        val original = UserSubscriptionProfile.fromJson(sampleJson)
        val serializedJson = original.toJson()
        val parsedAgain = UserSubscriptionProfile.fromJson(serializedJson)

        assertEquals(original.email, parsedAgain.email)
        assertEquals(original.subscriptionStatus, parsedAgain.subscriptionStatus)
        assertEquals(original.expiresAt, parsedAgain.expiresAt)
        assertEquals(original.linkedDevices.size, parsedAgain.linkedDevices.size)
        assertEquals(original.linkedDevices["core_device_id_xyz"]?.role, parsedAgain.linkedDevices["core_device_id_xyz"]?.role)
        assertEquals(original.linkedDevices["terminal_device_id_abc"]?.status, parsedAgain.linkedDevices["terminal_device_id_abc"]?.status)
    }

    @Test
    fun testFallbackOnMalformedJson() {
        val fallback = UserSubscriptionProfile.fromJson("INVALID_JSON")
        assertEquals("usuario@example.com", fallback.email)
        assertEquals("active", fallback.subscriptionStatus)
        assertTrue(fallback.linkedDevices.isNotEmpty())
    }
}
