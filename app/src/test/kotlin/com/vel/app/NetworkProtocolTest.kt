package com.vel.app

import com.vel.app.network.RelayConnectionState
import com.vel.app.network.WirePacket
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class NetworkProtocolTest {

    @Test
    fun testWirePacketCreationAndIntegrity() {
        val operationId = UUID.randomUUID().toString()
        val packet = WirePacket(
            type = "ENCRYPTED_MESSAGE",
            senderId = "VEL-CORE-01",
            recipientId = "VEL-TERM-01",
            sessionId = "session-test",
            operationId = operationId,
            iv = "base64-iv-test",
            ciphertext = "base64-ciphertext-test",
            status = "PENDING"
        )

        assertEquals("ENCRYPTED_MESSAGE", packet.type)
        assertEquals("VEL-CORE-01", packet.senderId)
        assertEquals("VEL-TERM-01", packet.recipientId)
        assertEquals(operationId, packet.operationId)
        assertNotNull(packet.iv)
        assertNotNull(packet.ciphertext)
        assertTrue(packet.timestamp > 0L)
    }

    @Test
    fun testRelayConnectionStates() {
        val states = RelayConnectionState.values()
        assertTrue(states.contains(RelayConnectionState.DISCONNECTED))
        assertTrue(states.contains(RelayConnectionState.CONNECTING))
        assertTrue(states.contains(RelayConnectionState.CONNECTED))
        assertTrue(states.contains(RelayConnectionState.AUTHENTICATED))
        assertTrue(states.contains(RelayConnectionState.REVOKED))
        assertTrue(states.contains(RelayConnectionState.ERROR))
    }
}
