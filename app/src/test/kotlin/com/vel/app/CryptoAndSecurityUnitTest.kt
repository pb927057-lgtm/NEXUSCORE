package com.vel.app

import com.vel.app.crypto.AesGcm
import com.vel.app.crypto.Hkdf
import com.vel.app.crypto.KeyAgreementManager
import com.vel.app.pairing.PairingManager
import com.vel.app.pairing.PairingProcessResult
import com.vel.app.pairing.PairingRepository
import com.vel.app.pairing.PairingSession
import com.vel.app.security.IdentityManager
import com.vel.app.ui.navigation.DeviceRole
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

class CryptoAndSecurityUnitTest {

    private lateinit var identityManagerCore: IdentityManager
    private lateinit var identityManagerTerminal: IdentityManager
    private lateinit var repositoryCore: PairingRepository
    private lateinit var repositoryTerminal: PairingRepository
    private lateinit var pairingManagerCore: PairingManager
    private lateinit var pairingManagerTerminal: PairingManager

    @Before
    fun setUp() {
        identityManagerCore = IdentityManager(null)
        identityManagerTerminal = IdentityManager(null)
        repositoryCore = PairingRepository(null)
        repositoryTerminal = PairingRepository(null)
        pairingManagerCore = PairingManager(identityManagerCore, repositoryCore)
        pairingManagerTerminal = PairingManager(identityManagerTerminal, repositoryTerminal)
    }

    // 1. Creación y persistencia de la identidad dentro de los límites del entorno de pruebas
    @Test
    fun testIdentityCreationAndProperties() {
        val identity = identityManagerCore.getOrCreateIdentity(DeviceRole.CORE)

        assertNotNull("La identidad no debe ser nula", identity)
        assertTrue("El ID debe comenzar con VEL-CORE", identity.deviceId.startsWith("VEL-CORE"))
        assertEquals("El rol debe ser CORE", DeviceRole.CORE, identity.role)
        assertTrue("La clave pública Base64 no debe estar vacía", identity.publicKeyBase64.isNotEmpty())
        assertTrue("La huella digital no debe estar vacía", identity.fingerprint.isNotEmpty())
        assertTrue("El timestamp de creación debe ser válido", identity.createdAt > 0L)

        // Verificar idempotencia: solicitarla de nuevo devuelve la misma identidad
        val identity2 = identityManagerCore.getOrCreateIdentity(DeviceRole.CORE)
        assertEquals("La identidad debe persistir sin regenerar claves", identity.deviceId, identity2.deviceId)
        assertEquals("La clave pública debe ser idéntica", identity.publicKeyBase64, identity2.publicKeyBase64)
        assertEquals("La huella debe ser idéntica", identity.fingerprint, identity2.fingerprint)
    }

    // 2. Consistencia de la huella digital
    @Test
    fun testFingerprintConsistency() {
        val keyPair1 = KeyAgreementManager.generateEcP256KeyPair()
        val keyPair2 = KeyAgreementManager.generateEcP256KeyPair()

        val fp1A = KeyAgreementManager.computeFingerprint(keyPair1.public)
        val fp1B = KeyAgreementManager.computeFingerprint(keyPair1.public)
        val fp2 = KeyAgreementManager.computeFingerprint(keyPair2.public)

        // La misma clave genera exactamente la misma huella SHA-256
        assertEquals("La huella digital debe ser 100% determinista y consistente", fp1A, fp1B)
        // Claves distintas generan huellas distintas
        assertNotEquals("Claves distintas deben producir huellas distintas", fp1A, fp2)
        // Formato estándar hexadecimal con dos puntos
        assertTrue("El formato de huella debe contener delimitadores ':'", fp1A.contains(":"))
    }

    // 3. Intercambio de claves (ECDH P-256) y derivación idéntica del código SAS
    @Test
    fun testEcdhKeyAgreementAndSasMatch() {
        val coreKp = KeyAgreementManager.generateEcP256KeyPair()
        val terminalKp = KeyAgreementManager.generateEcP256KeyPair()

        // CORE calcula secreto compartido con la clave pública de Terminal
        val coreSharedSecret = KeyAgreementManager.computeSharedSecret(coreKp.private, terminalKp.public)

        // Terminal calcula secreto compartido con la clave pública de CORE
        val terminalSharedSecret = KeyAgreementManager.computeSharedSecret(terminalKp.private, coreKp.public)

        // Los secretos compartidos deben coincidir byte por byte
        assertArrayEquals("Los secretos compartidos ECDH deben coincidir con exactitud", coreSharedSecret, terminalSharedSecret)

        // Derivar clave de sesión AES-256 vía HKDF
        val coreSessionKey = KeyAgreementManager.deriveSessionKey(coreSharedSecret)
        val terminalSessionKey = KeyAgreementManager.deriveSessionKey(terminalSharedSecret)
        assertArrayEquals("Las claves de sesión derivadas deben coincidir", coreSessionKey, terminalSessionKey)
        assertEquals("La clave de sesión debe ser de 32 bytes (256 bits)", 32, coreSessionKey.size)

        // Derivar código SAS de 6 dígitos
        val coreSas = KeyAgreementManager.deriveShortAuthenticationString(coreSharedSecret)
        val terminalSas = KeyAgreementManager.deriveShortAuthenticationString(terminalSharedSecret)

        assertEquals("Los códigos SAS de 6 dígitos deben coincidir exactamente", coreSas, terminalSas)
        assertEquals("El código SAS debe tener exactamente 6 dígitos numéricos", 6, coreSas.length)
        assertTrue("El código SAS debe ser numérico", coreSas.all { it.isDigit() })
    }

    // 4. Cifrado y descifrado AES-GCM de 256 bits
    @Test
    fun testAesGcmEncryptionAndDecryption() {
        val key = ByteArray(32) { (it * 7).toByte() } // Clave de 256 bits
        val originalText = "Mensaje confidencial de prueba VEL 0.1".toByteArray(StandardCharsets.UTF_8)
        val associatedData = "metadata-auth".toByteArray(StandardCharsets.UTF_8)

        val encrypted = AesGcm.encrypt(key, originalText, associatedData)

        assertNotNull(encrypted)
        assertEquals("El IV debe tener exactamente 12 bytes (96 bits)", 12, encrypted.iv.size)
        assertTrue("El texto cifrado debe tener datos y el tag de 16 bytes", encrypted.ciphertextWithTag.size > originalText.size)

        // Descifrado correcto
        val decrypted = AesGcm.decrypt(key, encrypted.iv, encrypted.ciphertextWithTag, associatedData)
        assertArrayEquals("El texto descifrado debe coincidir exactamente con el original", originalText, decrypted)
        assertEquals("Mensaje confidencial de prueba VEL 0.1", String(decrypted, StandardCharsets.UTF_8))
    }

    // 5. Rechazo estricto de mensaje cifrado alterado (Tag de autenticación corrupto o datos manipulados)
    @Test
    fun testAesGcmRejectsTamperedCiphertext() {
        val key = ByteArray(32) { (it + 3).toByte() }
        val originalText = "Datos críticos que no deben ser manipulados".toByteArray(StandardCharsets.UTF_8)

        val encrypted = AesGcm.encrypt(key, originalText)

        // Alterar 1 bit en el texto cifrado
        val tamperedCiphertext = encrypted.ciphertextWithTag.clone()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0x01).toByte()

        try {
            AesGcm.decrypt(key, encrypted.iv, tamperedCiphertext)
            fail("Debe lanzar excepción al intentar descifrar datos manipulados")
        } catch (e: Exception) {
            assertTrue(
                "La excepción debe ser de tipo criptográfico AEADBadTagException o GeneralSecurityException",
                e is GeneralSecurityException || e is AEADBadTagException
            )
        }
    }

    // 6. Rechazo de código temporal vencido o reutilizado
    @Test
    fun testPairingCodeExpirationAndReplayRejection() {
        // A. Código vencido
        val now = System.currentTimeMillis()
        val expiredSession = PairingSession(
            sessionId = "test-session-expired",
            ephemeralCode = "VEL-9999",
            originRole = DeviceRole.CORE,
            initiatorDeviceId = "VEL-CORE-TEST",
            initiatorFingerprint = "AA:BB:CC",
            initiatorPublicKeyBase64 = KeyAgreementManager.encodePublicKey(KeyAgreementManager.generateEcP256KeyPair().public),
            createdAt = now - (10 * 60 * 1000L), // Creado hace 10 min
            expiresAt = now - (5 * 60 * 1000L),  // Venció hace 5 min
            isUsed = false
        )

        val resultExpired = pairingManagerTerminal.processPairingCode(
            inputCode = "VEL-9999",
            receiverRole = DeviceRole.TERMINAL,
            knownSession = expiredSession
        )

        assertTrue("Debe rechazar código expirado", resultExpired is PairingProcessResult.Error)
        val errorExpired = resultExpired as PairingProcessResult.Error
        assertTrue("El mensaje debe indicar expiración", errorExpired.message.contains("expirado"))

        // B. Código reutilizado
        val usedSession = PairingSession(
            sessionId = "test-session-used",
            ephemeralCode = "VEL-8888",
            originRole = DeviceRole.CORE,
            initiatorDeviceId = "VEL-CORE-TEST",
            initiatorFingerprint = "AA:BB:CC",
            initiatorPublicKeyBase64 = KeyAgreementManager.encodePublicKey(KeyAgreementManager.generateEcP256KeyPair().public),
            createdAt = now,
            expiresAt = now + (5 * 60 * 1000L),
            isUsed = true // Ya usado
        )

        val resultUsed = pairingManagerTerminal.processPairingCode(
            inputCode = "VEL-8888",
            receiverRole = DeviceRole.TERMINAL,
            knownSession = usedSession
        )

        assertTrue("Debe rechazar código reutilizado", resultUsed is PairingProcessResult.Error)
        val errorUsed = resultUsed as PairingProcessResult.Error
        assertTrue("El mensaje debe indicar que ya fue utilizado", errorUsed.message.contains("utilizado previamente"))
    }

    // 7. Flujo completo de confirmación de emparejamiento y revocación local
    @Test
    fun testPairingConfirmationAndLocalRevocation() {
        // 1. CORE crea sesión
        val coreSession = pairingManagerCore.createPairingSession(DeviceRole.CORE)
        assertFalse("La sesión no debe estar expirada", coreSession.isExpired)
        assertFalse("La sesión no debe estar usada", coreSession.isUsed)

        // 2. Terminal procesa código
        val result = pairingManagerTerminal.processPairingCode(
            inputCode = coreSession.ephemeralCode,
            receiverRole = DeviceRole.TERMINAL,
            knownSession = coreSession
        )
        assertTrue("El procesamiento debe esperar confirmación con SAS", result is PairingProcessResult.SuccessAwaitingConfirmation)
        val await = result as PairingProcessResult.SuccessAwaitingConfirmation

        // 3. Confirmar emparejamiento
        val pairedDevice = pairingManagerTerminal.confirmPairing(
            session = await.session,
            sasCode = await.sasCode,
            customDeviceName = "Mi VEL CORE"
        )
        assertNotNull(pairedDevice)
        assertTrue("La sesión debe quedar marcada como consumida", await.session.isUsed)
        assertEquals("Emparejado y verificado", pairedDevice.statusText)
        assertFalse(pairedDevice.isLocallyRevoked)

        // Verificar persistencia en repositorio
        val storedDevices = repositoryTerminal.getPairedDevices()
        assertEquals(1, storedDevices.size)
        assertEquals(pairedDevice.id, storedDevices.first().id)

        // 4. Revocación local
        val revoked = repositoryTerminal.revokeDeviceLocally(pairedDevice.id)
        assertTrue("La revocación local debe ser exitosa", revoked)
        val deviceAfterRevoke = repositoryTerminal.getPairedDevices().first()
        assertTrue(deviceAfterRevoke.isLocallyRevoked)
        assertEquals("Revocado localmente", deviceAfterRevoke.statusText)
    }
}
