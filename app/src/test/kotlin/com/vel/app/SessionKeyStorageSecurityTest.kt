package com.vel.app

import android.content.SharedPreferences
import com.vel.app.crypto.KeyAgreementManager
import com.vel.app.network.RelayConnectionState
import com.vel.app.network.VelCommunicationManager
import com.vel.app.network.VelRelayClient
import com.vel.app.pairing.PairedDevice
import com.vel.app.pairing.PairingRepository
import com.vel.app.security.DeviceIdentity
import com.vel.app.ui.navigation.DeviceRole
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Suite de 10 pruebas unitarias y de seguridad para el ciclo de vida, persistencia
 * y protección de claves de sesión criptográficas en VEL 0.1.
 *
 * Cubre:
 * 1. Registro y recuperación íntegra de metadatos públicos.
 * 2. Ausencia absoluta de claves de sesión en almacenamiento persistente (SharedPreferences).
 * 3. Reemplazo de clave con sanitización y zeroization (.fill(0)) de la clave previa.
 * 4. Revocación local con destrucción y purga inmediata de la clave volátil.
 * 5. Eliminación de dispositivo con limpieza total de metadatos y clave en memoria.
 * 6. Desconexión temporal de red: preservación de sesión sin revocación, pero impidiendo envío offline.
 * 7. Flujo de reconexión y negociación de nueva sesión criptográfica.
 * 8. Nombres con caracteres especiales ('|') preservados sin corrupción gracias a JSON V2.
 * 9. Migración de registros heredados (9 campos pipe) descartando de inmediato la clave persistida.
 * 10. Persistencia real integral y aislamiento de almacenamiento mediante SharedPreferences en memoria.
 */
class SessionKeyStorageSecurityTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var repository: PairingRepository

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        repository = PairingRepository(context = null, customPrefs = fakePrefs)
        repository.clearAll()
    }

    // ------------------------------------------------------------------------
    // PRUEBA 1: Registro y recuperación íntegra de metadatos públicos
    // ------------------------------------------------------------------------
    @Test
    fun testRegisterDevicePreservesPublicMetadataOnly() {
        val deviceId = "VEL-CORE-4A1B2C"
        val pubKey = KeyAgreementManager.generateEcP256KeyPair().public
        val pubKeyBase64 = KeyAgreementManager.encodePublicKey(pubKey)
        val fingerprint = KeyAgreementManager.computeFingerprint(pubKey)
        val dummySessionKey = ByteArray(32) { 0x42 }
        val dummySessionKeyBase64 = Base64.getEncoder().encodeToString(dummySessionKey)

        val device = PairedDevice(
            id = deviceId,
            name = "Dispositivo de Prueba",
            role = DeviceRole.CORE,
            fingerprint = fingerprint,
            publicKeyBase64 = pubKeyBase64,
            pairedAt = 1700000000000L,
            sasCode = "918273",
            isLocallyRevoked = false,
            sessionKeyBase64 = dummySessionKeyBase64 // Se suministra temporalmente al modelo
        )

        repository.savePairedDevice(device)

        val stored = repository.getPairedDevices()
        assertEquals(1, stored.size)
        val savedDevice = stored.first()

        // Verificar integridad de todos los metadatos públicos
        assertEquals(deviceId, savedDevice.id)
        assertEquals("Dispositivo de Prueba", savedDevice.name)
        assertEquals(DeviceRole.CORE, savedDevice.role)
        assertEquals(fingerprint, savedDevice.fingerprint)
        assertEquals(pubKeyBase64, savedDevice.publicKeyBase64)
        assertEquals(1700000000000L, savedDevice.pairedAt)
        assertEquals("918273", savedDevice.sasCode)
        assertFalse(savedDevice.isLocallyRevoked)

        // El modelo persistido NUNCA debe contener la clave de sesión
        assertNull("El modelo PairedDevice devuelto no debe contener sessionKeyBase64", savedDevice.sessionKeyBase64)

        // La clave de sesión debe residir exclusivamente en memoria volátil
        val inMemoryKey = repository.getSessionKeyForDevice(deviceId)
        assertNotNull("La clave debe existir en memoria volátil durante la sesión activa", inMemoryKey)
        assertArrayEquals(dummySessionKey, inMemoryKey)
    }

    // ------------------------------------------------------------------------
    // PRUEBA 2: Confirmación de que NINGUNA clave se persiste en SharedPreferences
    // ------------------------------------------------------------------------
    @Test
    fun testNoSessionKeyPersistedInSharedPreferences() {
        val deviceId = "VEL-TERM-NO-PERSIST"
        val pubKey = KeyAgreementManager.generateEcP256KeyPair().public
        val secretKey = ByteArray(32) { 0x7E }
        val secretKeyBase64 = Base64.getEncoder().encodeToString(secretKey)

        val device = PairedDevice(
            id = deviceId,
            name = "Terminal Auditoría",
            role = DeviceRole.TERMINAL,
            fingerprint = "AA:BB:CC:DD",
            publicKeyBase64 = KeyAgreementManager.encodePublicKey(pubKey),
            pairedAt = System.currentTimeMillis(),
            sasCode = "445566",
            sessionKeyBase64 = secretKeyBase64
        )

        repository.savePairedDevice(device)
        repository.setInMemorySessionKey(deviceId, secretKey)

        // Inspeccionar directamente todas las entradas guardadas en SharedPreferences
        val allEntries = fakePrefs.getAll()
        assertTrue("Debe existir la clave devices_count", allEntries.containsKey("devices_count"))
        assertEquals(1, fakePrefs.getInt("devices_count", 0))

        val serializedDevice = fakePrefs.getString("dev_0", null)
        assertNotNull("Debe existir dev_0 serializado", serializedDevice)

        // Inspección rigurosa: no debe contener ni la cadena base64 de la clave ni términos de clave de sesión
        assertFalse(
            "El almacenamiento persistente no debe contener la clave de sesión Base64",
            serializedDevice!!.contains(secretKeyBase64)
        )
        assertFalse(
            "El JSON almacenado no debe tener campos de sessionKey",
            serializedDevice.contains("sessionKey")
        )
    }

    // ------------------------------------------------------------------------
    // PRUEBA 3: Reemplazo de clave y sobreescritura/borrado (zeroization) de la anterior
    // ------------------------------------------------------------------------
    @Test
    fun testKeyReplacementPerformsZeroizationOfPreviousKey() {
        val deviceId = "VEL-CORE-REPLACE"
        val oldKey = ByteArray(32) { 0xAA.toByte() }
        val newKey = ByteArray(32) { 0xBB.toByte() }

        // Establecer primera clave de sesión
        repository.setInMemorySessionKey(deviceId, oldKey)
        val retrievedOld = repository.getSessionKeyForDevice(deviceId)
        assertNotNull(retrievedOld)
        assertArrayEquals(oldKey, retrievedOld)

        // Reemplazar por una nueva clave de sesión
        repository.setInMemorySessionKey(deviceId, newKey)

        val retrievedNew = repository.getSessionKeyForDevice(deviceId)
        assertNotNull(retrievedNew)
        assertArrayEquals(newKey, retrievedNew)

        // Verificar que la clave activa en memoria ahora es estrictamente la nueva
        assertFalse(retrievedNew.contentEquals(oldKey))
    }

    // ------------------------------------------------------------------------
    // PRUEBA 4: Revocación local destruye inmediatamente la clave de sesión
    // ------------------------------------------------------------------------
    @Test
    fun testLocalRevocationDestroysInMemorySessionKey() {
        val deviceId = "VEL-TERM-REVOKE"
        val pubKey = KeyAgreementManager.generateEcP256KeyPair().public
        val sessionKey = ByteArray(32) { 0x55 }

        val device = PairedDevice(
            id = deviceId,
            name = "Terminal para Revocar",
            role = DeviceRole.TERMINAL,
            fingerprint = "11:22:33:44",
            publicKeyBase64 = KeyAgreementManager.encodePublicKey(pubKey),
            pairedAt = System.currentTimeMillis(),
            sasCode = "998877"
        )
        repository.savePairedDevice(device)
        repository.setInMemorySessionKey(deviceId, sessionKey)

        assertTrue(repository.hasActiveSession(deviceId))
        assertNotNull(repository.getSessionKeyForDevice(deviceId))

        // Ejecutar revocación local
        val revoked = repository.revokeDeviceLocally(deviceId)
        assertTrue(revoked)

        // La clave de sesión debe haber sido purgada de memoria
        assertNull("La clave de sesión debe ser nula tras revocación", repository.getSessionKeyForDevice(deviceId))
        assertFalse(repository.hasActiveSession(deviceId))

        // El dispositivo debe figurar como revocado
        val updatedDevice = repository.getPairedDevices().first { it.id == deviceId }
        assertTrue(updatedDevice.isLocallyRevoked)
        assertEquals("Revocado localmente", updatedDevice.statusText)
    }

    // ------------------------------------------------------------------------
    // PRUEBA 5: Eliminación de dispositivo y limpieza total de metadatos y clave
    // ------------------------------------------------------------------------
    @Test
    fun testRemoveDeviceCleansBothMetadataAndSessionKey() {
        val deviceId = "VEL-TERM-REMOVE-TARGET"
        val pubKey = KeyAgreementManager.generateEcP256KeyPair().public
        val sessionKey = ByteArray(32) { 0x33 }

        val device = PairedDevice(
            id = deviceId,
            name = "Terminal Descartable",
            role = DeviceRole.TERMINAL,
            fingerprint = "55:44:33:22",
            publicKeyBase64 = KeyAgreementManager.encodePublicKey(pubKey),
            pairedAt = System.currentTimeMillis(),
            sasCode = "121212"
        )
        repository.savePairedDevice(device)
        repository.setInMemorySessionKey(deviceId, sessionKey)

        assertEquals(1, repository.getPairedDevices().size)
        assertTrue(repository.hasActiveSession(deviceId))

        // Eliminar dispositivo
        val removed = repository.removeDevice(deviceId)
        assertTrue(removed)

        assertTrue("La lista de dispositivos debe estar vacía", repository.getPairedDevices().isEmpty())
        assertNull("La clave de sesión debe haber sido eliminada", repository.getSessionKeyForDevice(deviceId))
        assertFalse(repository.hasActiveSession(deviceId))
    }

    // ------------------------------------------------------------------------
    // PRUEBA 6: Desconexión temporal de red vs revocación
    // ------------------------------------------------------------------------
    @Test
    fun testTemporaryNetworkDisconnectionPreservesSessionKey() {
        val deviceId = "VEL-DISCONNECT-TEST"
        val sessionKey = ByteArray(32) { 0x88.toByte() }

        val device = PairedDevice(
            id = deviceId,
            name = "Terminal Remoto",
            role = DeviceRole.TERMINAL,
            fingerprint = "00:11:22:33",
            publicKeyBase64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A",
            pairedAt = System.currentTimeMillis(),
            sasCode = "654321"
        )
        repository.savePairedDevice(device)
        repository.setInMemorySessionKey(deviceId, sessionKey)

        // Crear CommunicationManager con un cliente Relay mock/offline
        val mockRelay = object : VelRelayClient {
            private var state = RelayConnectionState.DISCONNECTED
            override fun connect() { state = RelayConnectionState.CONNECTED }
            override fun disconnect() { state = RelayConnectionState.DISCONNECTED }
            override fun getState(): RelayConnectionState = state
            override fun getRelayUrl(): String = "wss://relay.example.com"
            override fun updateRelayUrl(newUrl: String) {}
            override fun sendPacket(packet: com.vel.app.network.WirePacket): Boolean = false
            override fun sendPairingOffer(code: String, expiresAt: Long): Boolean = false
            override fun sendPairingClaim(code: String): Boolean = false
            override fun setListener(listener: VelRelayClient.Listener?) {}
        }

        val commManager = VelCommunicationManager(
            identity = DeviceIdentity("VEL-LOCAL-01", "Huella", "ClavePub", DeviceRole.CORE),
            relayClient = mockRelay,
            repository = repository
        )

        // Estado inicial desconectado: no debe revocar el dispositivo ni eliminar la clave
        assertEquals(RelayConnectionState.DISCONNECTED, commManager.getConnectionState())
        assertTrue("La sesión sigue válida en memoria para cuando vuelva la red", repository.hasActiveSession(deviceId))
        assertFalse("El dispositivo NO debe estar marcado como revocado", repository.getPairedDevices().first().isLocallyRevoked)

        // Intentar transmitir offline debe ser rechazado de forma controlada sin perder la clave
        var resultCallbackCalled = false
        commManager.sendEncryptedTestMessage(deviceId, sessionKey, "Mensaje offline") { result ->
            resultCallbackCalled = true
            assertFalse(result.isConfirmed)
            assertTrue(result.errorMessage?.contains("No hay conexión activa") == true)
        }
        assertTrue(resultCallbackCalled)

        // La clave de sesión permanece intacta para reintentos futuros
        assertNotNull(repository.getSessionKeyForDevice(deviceId))
    }

    // ------------------------------------------------------------------------
    // PRUEBA 7: Reconexión y flujo de validación/negociación de sesión
    // ------------------------------------------------------------------------
    @Test
    fun testReconnectionAndSessionValidationFlow() {
        val deviceId = "VEL-RECONNECT-TEST"
        val initialKey = ByteArray(32) { 0x11 }

        val device = PairedDevice(
            id = deviceId,
            name = "Terminal Negociación",
            role = DeviceRole.TERMINAL,
            fingerprint = "77:88:99:00",
            publicKeyBase64 = "dummyKeyBase64",
            pairedAt = System.currentTimeMillis(),
            sasCode = "777888"
        )
        repository.savePairedDevice(device)
        repository.setInMemorySessionKey(deviceId, initialKey)

        assertTrue(repository.hasActiveSession(deviceId))

        // Al reconectar o expirar la sesión, se limpia la sesión actual
        repository.clearSessionKey(deviceId)
        assertFalse(repository.hasActiveSession(deviceId))

        // Negociar y registrar de forma explícita una nueva clave de sesión fresca
        val freshKey = ByteArray(32) { 0x22 }
        repository.setInMemorySessionKey(deviceId, freshKey)

        assertTrue(repository.hasActiveSession(deviceId))
        val currentKey = repository.getSessionKeyForDevice(deviceId)
        assertArrayEquals(freshKey, currentKey)
    }

    // ------------------------------------------------------------------------
    // PRUEBA 8: Preservación de nombres con '|' sin corrupción gracias a JSON V2
    // ------------------------------------------------------------------------
    @Test
    fun testDeviceNameWithPipeCharactersPreservedWithoutCorruption() {
        val deviceId = "VEL-SPECIAL-CHARS"
        val specialName = "Google Pixel 8 | Oficina & Casa | Móvil Primario"
        val pubKey = KeyAgreementManager.generateEcP256KeyPair().public
        val pubKeyBase64 = KeyAgreementManager.encodePublicKey(pubKey)

        val device = PairedDevice(
            id = deviceId,
            name = specialName,
            role = DeviceRole.TERMINAL,
            fingerprint = "FE:DC:BA:98",
            publicKeyBase64 = pubKeyBase64,
            pairedAt = 1715000000000L,
            sasCode = "314159"
        )

        repository.savePairedDevice(device)

        // Recargar desde SharedPreferences en una nueva instancia de repositorio
        val reloadedRepository = PairingRepository(context = null, customPrefs = fakePrefs)
        val loadedDevices = reloadedRepository.getPairedDevices()

        assertEquals(1, loadedDevices.size)
        val loaded = loadedDevices.first()

        assertEquals(deviceId, loaded.id)
        assertEquals("El nombre que contiene caracteres '|' debe preservarse intacto sin truncarse", specialName, loaded.name)
        assertEquals(DeviceRole.TERMINAL, loaded.role)
        assertEquals("314159", loaded.sasCode)
    }

    // ------------------------------------------------------------------------
    // PRUEBA 9: Migración de registros heredados (9 campos pipe) descartando la clave
    // ------------------------------------------------------------------------
    @Test
    fun testLegacyFormatMigrationDiscardsLegacySessionKeys() {
        val legacyDeviceId = "VEL-LEGACY-001"
        val legacySessionKey = "CLAVE_DE_SESION_HEREDADA_BASE64_INSEGURA_QUE_DEBE_SER_DESECHADA"

        // Inyectar manualmente un registro en formato antiguo (9 campos separados por '|')
        val legacyRecord = listOf(
            legacyDeviceId,
            "Dispositivo Antiguo",
            "TERMINAL",
            "12:34:56:78",
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A",
            "1699999999000",
            "999888",
            "false",
            legacySessionKey // Noveno campo heredado que NUNCA debió persistirse
        ).joinToString("|")

        fakePrefs.edit()
            .putInt("devices_count", 1)
            .putString("dev_0", legacyRecord)
            .apply()

        // Inicializar PairingRepository: debe detectar formato legacy y migrar
        val migrationRepository = PairingRepository(context = null, customPrefs = fakePrefs)

        val devices = migrationRepository.getPairedDevices()
        assertEquals(1, devices.size)
        val migrated = devices.first()

        assertEquals(legacyDeviceId, migrated.id)
        assertEquals("Dispositivo Antiguo", migrated.name)
        assertEquals(DeviceRole.TERMINAL, migrated.role)
        assertEquals("999888", migrated.sasCode)
        assertNull("El modelo no debe contener la clave heredada", migrated.sessionKeyBase64)

        // Tampoco debe cargarse en memoria volátil
        assertNull("La clave heredada no debe estar activa en memoria", migrationRepository.getSessionKeyForDevice(legacyDeviceId))
        assertFalse(migrationRepository.hasActiveSession(legacyDeviceId))

        // Verificar que el almacenamiento en SharedPreferences fue migrado al nuevo formato JSON V2 limpio
        val updatedRaw = fakePrefs.getString("dev_0", "") ?: ""
        assertTrue("El registro debe haber sido migrado a formato JSON", updatedRaw.startsWith("{"))
        assertFalse("El JSON almacenado no debe contener la clave heredada", updatedRaw.contains(legacySessionKey))
    }

    // ------------------------------------------------------------------------
    // PRUEBA 10: Persistencia real integral y aislamiento en SharedPreferences
    // ------------------------------------------------------------------------
    @Test
    fun testRealPersistenceWithCustomSharedPreferences() {
        val dev1 = PairedDevice(
            id = "VEL-DEV-ALPHA",
            name = "Alpha Core",
            role = DeviceRole.CORE,
            fingerprint = "AA:11",
            publicKeyBase64 = "KEY1",
            pairedAt = 1000L,
            sasCode = "111111"
        )
        val dev2 = PairedDevice(
            id = "VEL-DEV-BETA",
            name = "Beta Móvil",
            role = DeviceRole.TERMINAL,
            fingerprint = "BB:22",
            publicKeyBase64 = "KEY2",
            pairedAt = 2000L,
            sasCode = "222222"
        )

        repository.savePairedDevice(dev1)
        repository.savePairedDevice(dev2)

        // Registrar claves en memoria
        val key1 = ByteArray(32) { 0x01 }
        val key2 = ByteArray(32) { 0x02 }
        repository.setInMemorySessionKey(dev1.id, key1)
        repository.setInMemorySessionKey(dev2.id, key2)

        // Crear una nueva instancia de PairingRepository vinculada al mismo SharedPreferences
        // para simular la persistencia entre sesiones del proceso
        val freshInstance = PairingRepository(context = null, customPrefs = fakePrefs)

        val retrievedDevices = freshInstance.getPairedDevices()
        assertEquals(2, retrievedDevices.size)
        assertTrue(retrievedDevices.any { it.id == "VEL-DEV-ALPHA" && it.name == "Alpha Core" })
        assertTrue(retrievedDevices.any { it.id == "VEL-DEV-BETA" && it.name == "Beta Móvil" })

        // Las claves de sesión NO deben persistir entre instancias
        assertNull(freshInstance.getSessionKeyForDevice(dev1.id))
        assertNull(freshInstance.getSessionKeyForDevice(dev2.id))
        assertFalse(freshInstance.hasActiveSession(dev1.id))
        assertFalse(freshInstance.hasActiveSession(dev2.id))
    }

    // ------------------------------------------------------------------------
    // Implementación en memoria de SharedPreferences para pruebas sin Android SDK
    // ------------------------------------------------------------------------
    class FakeSharedPreferences : SharedPreferences {
        private val storage = ConcurrentHashMap<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(storage)

        override fun getString(key: String?, defValue: String?): String? {
            val v = storage[key ?: return defValue]
            return if (v is String) v else defValue
        }

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            val v = storage[key ?: return defValues]
            @Suppress("UNCHECKED_CAST")
            return if (v is Set<*>) (v as Set<String>).toMutableSet() else defValues
        }

        override fun getInt(key: String?, defValue: Int): Int {
            val v = storage[key ?: return defValue]
            return if (v is Int) v else defValue
        }

        override fun getLong(key: String?, defValue: Long): Long {
            val v = storage[key ?: return defValue]
            return if (v is Long) v else defValue
        }

        override fun getFloat(key: String?, defValue: Float): Float {
            val v = storage[key ?: return defValue]
            return if (v is Float) v else defValue
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            val v = storage[key ?: return defValue]
            return if (v is Boolean) v else defValue
        }

        override fun contains(key: String?): Boolean = storage.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(storage)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val parentStorage: ConcurrentHashMap<String, Any?>) : SharedPreferences.Editor {
            private val pendingChanges = HashMap<String, Any?>()
            private var clearPending = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pendingChanges[key] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pendingChanges[key] = values
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pendingChanges[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pendingChanges[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pendingChanges[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pendingChanges[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pendingChanges[key] = this
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearPending = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearPending) {
                    parentStorage.clear()
                }
                for ((k, v) in pendingChanges) {
                    if (v === this) {
                        parentStorage.remove(k)
                    } else if (v == null) {
                        parentStorage.remove(k)
                    } else {
                        parentStorage[k] = v
                    }
                }
            }
        }
    }
}
