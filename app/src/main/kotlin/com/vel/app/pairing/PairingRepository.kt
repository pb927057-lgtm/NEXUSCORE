package com.vel.app.pairing

import android.content.Context
import android.content.SharedPreferences
import com.vel.app.ui.navigation.DeviceRole
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Repositorio de dispositivos de confianza emparejados.
 *
 * POLÍTICA DE SEGURIDAD CRIPTOGRÁFICA (VEL 0.1):
 * 1. Persiste EXCLUSIVAMENTE metadatos públicos de dispositivos emparejados:
 *    ID, nombre asignado, rol, huella digital SHA-256, clave pública Base64 (X.509),
 *    fecha de emparejamiento, código SAS y estado de revocación local.
 * 2. NINGUNA clave criptográfica de sesión se almacena de forma persistente en
 *    SharedPreferences, SQLite, archivos locales ni almacenamiento web.
 * 3. Las claves de sesión simétricas existen ÚNICAMENTE EN MEMORIA durante la ejecución activa.
 * 4. Al reiniciar la aplicación, no se recupera ninguna clave de sesión anterior.
 * 5. Si existían registros heredados con claves persistidas en disco, se migran automáticamente
 *    descartando las claves de sesión y conservando únicamente los metadatos públicos.
 */
class PairingRepository(
    private val context: Context? = null,
    private val customPrefs: SharedPreferences? = null
) {

    companion object {
        private const val PREFS_NAME = "vel_pairing_store"
        private const val KEY_DEVICES_COUNT = "devices_count"
        private const val PREFIX_DEVICE = "dev_"

        @Volatile
        private var instance: PairingRepository? = null

        fun getInstance(context: Context? = null): PairingRepository {
            return instance ?: synchronized(this) {
                instance ?: PairingRepository(context?.applicationContext ?: context).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences? by lazy {
        customPrefs ?: context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Metadatos públicos de dispositivos de confianza
    private val inMemoryDevices = CopyOnWriteArrayList<PairedDevice>()

    // Claves simétricas de sesión: ÚNICA AUTORIDAD, estrictamente volátiles EN MEMORIA (nunca persistidas)
    private val inMemorySessionKeys = ConcurrentHashMap<String, ByteArray>()

    init {
        loadFromStorage()
    }

    /**
     * Devuelve la lista de dispositivos emparejados (únicamente metadatos públicos).
     */
    @Synchronized
    fun getPairedDevices(): List<PairedDevice> {
        return inMemoryDevices.toList()
    }

    /**
     * Registra o actualiza los metadatos de un dispositivo emparejado.
     * Si el objeto recibido incluye temporalmente una clave de sesión en memoria,
     * se conserva exclusivamente en el mapa volátil [inMemorySessionKeys] con copia defensiva y zeroization.
     */
    @Synchronized
    fun savePairedDevice(device: PairedDevice) {
        // Si el llamador proporcionó una clave en memoria, retenerla solo en la estructura volátil
        if (!device.sessionKeyBase64.isNullOrEmpty()) {
            try {
                val decodedKey = java.util.Base64.getDecoder().decode(device.sessionKeyBase64)
                setInMemorySessionKey(device.id, decodedKey)
                decodedKey.fill(0)
            } catch (_: Throwable) {}
        }

        // Sanitizar objeto para garantizar que la clave de sesión nunca se retenga en metadatos persistentes
        val sanitized = device.copy(sessionKeyBase64 = null)

        val existingIndex = inMemoryDevices.indexOfFirst { it.id == sanitized.id }
        if (existingIndex >= 0) {
            inMemoryDevices[existingIndex] = sanitized
        } else {
            inMemoryDevices.add(sanitized)
        }
        persistToStorage()
    }

    /**
     * Registra una clave de sesión simétrica EXCLUSIVAMENTE en memoria volátil.
     * Realiza copia defensiva y sobrescribe/limpia (zeroization con fill(0)) la clave previa si existía.
     */
    fun setInMemorySessionKey(deviceId: String, key: ByteArray) {
        val defensiveCopy = key.clone()
        val oldKey = inMemorySessionKeys.put(deviceId, defensiveCopy)
        oldKey?.fill(0)
    }

    /**
     * Obtiene la clave de sesión simétrica activa en memoria para un dispositivo.
     * Retorna una copia defensiva para evitar mutaciones externas del estado interno.
     * Retorna null si no existe una sesión establecida en la ejecución actual.
     */
    fun getSessionKeyForDevice(deviceId: String): ByteArray? {
        return inMemorySessionKeys[deviceId]?.clone()
    }

    /**
     * Consulta si un dispositivo tiene una sesión criptográfica activa en memoria.
     */
    fun hasActiveSession(deviceId: String): Boolean {
        return inMemorySessionKeys.containsKey(deviceId)
    }

    /**
     * Descarta la clave de sesión en memoria de un dispositivo específico, aplicando zeroization.
     */
    fun clearSessionKey(deviceId: String) {
        val removed = inMemorySessionKeys.remove(deviceId)
        removed?.fill(0)
    }

    /**
     * Descarta todas las claves de sesión en memoria, aplicando zeroization exhaustivo.
     */
    fun clearAllSessionKeys() {
        inMemorySessionKeys.values.forEach { it.fill(0) }
        inMemorySessionKeys.clear()
    }

    /**
     * Revoca localmente un dispositivo y destruye de inmediato su clave de sesión en memoria mediante zeroization.
     */
    @Synchronized
    fun revokeDeviceLocally(deviceId: String): Boolean {
        val removed = inMemorySessionKeys.remove(deviceId)
        removed?.fill(0)

        val index = inMemoryDevices.indexOfFirst { it.id == deviceId }
        if (index >= 0) {
            val current = inMemoryDevices[index]
            val updated = current.copy(
                isLocallyRevoked = true,
                statusText = "Revocado localmente",
                sessionKeyBase64 = null
            )
            inMemoryDevices[index] = updated
            persistToStorage()
            return true
        }
        return false
    }

    /**
     * Elimina un dispositivo emparejado y descarta cualquier clave de sesión asociada con zeroization.
     */
    @Synchronized
    fun removeDevice(deviceId: String): Boolean {
        val removedKey = inMemorySessionKeys.remove(deviceId)
        removedKey?.fill(0)
        val removed = inMemoryDevices.removeIf { it.id == deviceId }
        if (removed) {
            persistToStorage()
        }
        return removed
    }

    /**
     * Limpia completamente el repositorio y el almacenamiento de SharedPreferences.
     */
    @Synchronized
    fun clearAll() {
        inMemoryDevices.clear()
        clearAllSessionKeys()
        prefs?.edit()?.clear()?.apply()
    }

    /**
     * Carga exclusivamente metadatos públicos desde SharedPreferences.
     * Soporta formato robusto JSON V2 y realiza migración limpia automática de registros
     * heredados (formato pipe V1), descartando cualquier clave de sesión persistida antigua.
     */
    private fun loadFromStorage() {
        val p = prefs ?: return
        val count = p.getInt(KEY_DEVICES_COUNT, 0)
        inMemoryDevices.clear()
        // NOTA: inMemorySessionKeys permanece vacío al reiniciar; no se recupera ninguna clave anterior.

        var needsMigrationToV2 = false

        for (i in 0 until count) {
            val serialized = p.getString("$PREFIX_DEVICE$i", null) ?: continue
            try {
                val trimmed = serialized.trim()
                if (trimmed.startsWith("{")) {
                    // Formato robusto JSON V2
                    val json = org.json.JSONObject(trimmed)
                    val id = json.getString("id")
                    val name = json.getString("name")
                    val role = DeviceRole.valueOf(json.getString("role"))
                    val fingerprint = json.getString("fingerprint")
                    val publicKeyBase64 = json.getString("publicKeyBase64")
                    val pairedAt = json.optLong("pairedAt", System.currentTimeMillis())
                    val sasCode = json.getString("sasCode")
                    val isLocallyRevoked = json.optBoolean("isLocallyRevoked", false)

                    val device = PairedDevice(
                        id = id,
                        name = name,
                        role = role,
                        fingerprint = fingerprint,
                        publicKeyBase64 = publicKeyBase64,
                        pairedAt = pairedAt,
                        sasCode = sasCode,
                        isLocallyRevoked = isLocallyRevoked,
                        statusText = if (isLocallyRevoked) "Revocado localmente" else "Emparejado y verificado",
                        sessionKeyBase64 = null // NUNCA recuperado de almacenamiento
                    )
                    inMemoryDevices.add(device)
                } else {
                    // Formato heredado con delimitador '|'
                    val parts = serialized.split("|")
                    if (parts.size >= 8) {
                        needsMigrationToV2 = true
                        // Si parts.size >= 9, parts[8] era la clave persistida heredada; se descarta aquí.
                        val device = PairedDevice(
                            id = parts[0],
                            name = parts[1],
                            role = DeviceRole.valueOf(parts[2]),
                            fingerprint = parts[3],
                            publicKeyBase64 = parts[4],
                            pairedAt = parts[5].toLongOrNull() ?: System.currentTimeMillis(),
                            sasCode = parts[6],
                            isLocallyRevoked = parts[7].toBoolean(),
                            statusText = if (parts[7].toBoolean()) "Revocado localmente" else "Emparejado y verificado",
                            sessionKeyBase64 = null
                        )
                        inMemoryDevices.add(device)
                    }
                }
            } catch (_: Exception) {
                // Ignorar entradas corruptas
            }
        }

        // Si existían registros heredados, migrar inmediatamente a formato JSON V2 limpio
        if (needsMigrationToV2) {
            persistToStorage()
        }
    }

    /**
     * Persiste en SharedPreferences ÚNICAMENTE los metadatos públicos en formato JSON V2.
     * Bajo ninguna circunstancia se escribe una clave de sesión en disco.
     */
    private fun persistToStorage() {
        val editor = prefs?.edit() ?: return
        editor.clear()
        editor.putInt(KEY_DEVICES_COUNT, inMemoryDevices.size)

        inMemoryDevices.forEachIndexed { index, device ->
            val json = org.json.JSONObject().apply {
                put("v", 2)
                put("id", device.id)
                put("name", device.name)
                put("role", device.role.name)
                put("fingerprint", device.fingerprint)
                put("publicKeyBase64", device.publicKeyBase64)
                put("pairedAt", device.pairedAt)
                put("sasCode", device.sasCode)
                put("isLocallyRevoked", device.isLocallyRevoked)
            }
            editor.putString("$PREFIX_DEVICE$index", json.toString())
        }
        editor.apply()
    }
}
