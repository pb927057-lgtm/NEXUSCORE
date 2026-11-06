package com.vel.app.data

import android.content.Context

/**
 * Repositorio de soberanía local para VEL en Android.
 * Proporciona acceso a registros cifrados locales respetando la arquitectura Offline-First.
 * Coherente con VelRepository en TypeScript.
 */
object VelRepository {

    private var database: VelDatabase? = null

    fun initialize(context: Context) {
        if (database == null) {
            database = VelDatabase.getInstance(context)
        }
    }

    private fun getDb(): VelDatabase {
        return database ?: VelDatabase.getInstance(null)
    }

    fun saveEncryptedItem(id: String, encryptedData: String, iv: String) {
        val record = VelLocalRecord(
            id = id,
            encryptedData = encryptedData,
            iv = iv,
            updatedAt = System.currentTimeMillis()
        )
        getDb().save(record)
    }

    fun getEncryptedItem(id: String): VelLocalRecord? {
        return getDb().get(id)
    }

    fun deleteItem(id: String) {
        getDb().remove(id)
    }

    fun getAllEncryptedItems(): List<VelLocalRecord> {
        return getDb().getAll()
    }

    fun count(): Int {
        return getDb().count()
    }
}
