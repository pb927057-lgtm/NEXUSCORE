package com.vel.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.ConcurrentHashMap

/**
 * Modelo de registro con Soberanía Local (Offline-First) para VEL.
 * Almacena exclusivamente datos cifrados (AES-256-GCM) para preservar
 * la soberanía del usuario en el dispositivo sin enviar texto plano al relay.
 */
data class VelLocalRecord(
    val id: String,
    val encryptedData: String, // Datos cifrados (Soberanía local)
    val iv: String,            // Vector de inicialización para AES
    val updatedAt: Long
)

/**
 * Base de datos local SQLite para Android (Offline-First).
 * Incluye fallback en memoria si el contexto es nulo (para pruebas unitarias).
 */
class VelDatabase(context: Context? = null) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "VelLocalDB.db"
        private const val DB_VERSION = 1
        private const val TABLE_RECORDS = "records"

        private const val COL_ID = "id"
        private const val COL_ENCRYPTED_DATA = "encryptedData"
        private const val COL_IV = "iv"
        private const val COL_UPDATED_AT = "updatedAt"

        @Volatile
        private var instance: VelDatabase? = null

        fun getInstance(context: Context? = null): VelDatabase {
            return instance ?: synchronized(this) {
                instance ?: VelDatabase(context?.applicationContext ?: context).also { instance = it }
            }
        }
    }

    private val inMemoryFallback = ConcurrentHashMap<String, VelLocalRecord>()
    private val isUsingFallback: Boolean = context == null

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE IF NOT EXISTS $TABLE_RECORDS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_ENCRYPTED_DATA TEXT NOT NULL,
                $COL_IV TEXT NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RECORDS")
        onCreate(db)
    }

    @Synchronized
    fun save(record: VelLocalRecord) {
        if (isUsingFallback) {
            inMemoryFallback[record.id] = record
            return
        }

        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_ID, record.id)
                put(COL_ENCRYPTED_DATA, record.encryptedData)
                put(COL_IV, record.iv)
                put(COL_UPDATED_AT, record.updatedAt)
            }
            db.insertWithOnConflict(TABLE_RECORDS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            inMemoryFallback[record.id] = record
        }
    }

    @Synchronized
    fun get(id: String): VelLocalRecord? {
        if (isUsingFallback) {
            return inMemoryFallback[id]
        }

        return try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_RECORDS,
                arrayOf(COL_ID, COL_ENCRYPTED_DATA, COL_IV, COL_UPDATED_AT),
                "$COL_ID = ?",
                arrayOf(id),
                null,
                null,
                null
            )
            cursor.use {
                if (it.moveToFirst()) {
                    VelLocalRecord(
                        id = it.getString(it.getColumnIndexOrThrow(COL_ID)),
                        encryptedData = it.getString(it.getColumnIndexOrThrow(COL_ENCRYPTED_DATA)),
                        iv = it.getString(it.getColumnIndexOrThrow(COL_IV)),
                        updatedAt = it.getLong(it.getColumnIndexOrThrow(COL_UPDATED_AT))
                    )
                } else {
                    inMemoryFallback[id]
                }
            }
        } catch (e: Exception) {
            inMemoryFallback[id]
        }
    }

    @Synchronized
    fun remove(id: String) {
        if (isUsingFallback) {
            inMemoryFallback.remove(id)
            return
        }

        try {
            val db = writableDatabase
            db.delete(TABLE_RECORDS, "$COL_ID = ?", arrayOf(id))
            inMemoryFallback.remove(id)
        } catch (e: Exception) {
            inMemoryFallback.remove(id)
        }
    }

    @Synchronized
    fun getAll(): List<VelLocalRecord> {
        if (isUsingFallback) {
            return inMemoryFallback.values.toList()
        }

        val records = mutableListOf<VelLocalRecord>()
        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_RECORDS,
                arrayOf(COL_ID, COL_ENCRYPTED_DATA, COL_IV, COL_UPDATED_AT),
                null,
                null,
                null,
                null,
                "$COL_UPDATED_AT DESC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    records.add(
                        VelLocalRecord(
                            id = it.getString(it.getColumnIndexOrThrow(COL_ID)),
                            encryptedData = it.getString(it.getColumnIndexOrThrow(COL_ENCRYPTED_DATA)),
                            iv = it.getString(it.getColumnIndexOrThrow(COL_IV)),
                            updatedAt = it.getLong(it.getColumnIndexOrThrow(COL_UPDATED_AT))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            return inMemoryFallback.values.toList()
        }
        return records
    }

    @Synchronized
    fun count(): Int {
        if (isUsingFallback) {
            return inMemoryFallback.size
        }
        return try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_RECORDS", null)
            cursor.use {
                if (it.moveToFirst()) it.getInt(0) else inMemoryFallback.size
            }
        } catch (e: Exception) {
            inMemoryFallback.size
        }
    }
}
