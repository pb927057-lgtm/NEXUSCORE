package com.vel.app

import com.vel.app.data.VelRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pruebas unitarias para VelRepository en Android.
 * Verifica la soberanía local de los datos y el almacenamiento sin conexión a la nube.
 */
class VelRepositoryTest {

    @Test
    fun testSaveAndRetrieveEncryptedItem() {
        val itemId = "nota-secreta-1"
        val textoCifrado = "DATOS_CIFRADOS_CON_AES_GCM"
        val iv = "iv-aleatorio"

        // Guardar localmente
        VelRepository.saveEncryptedItem(itemId, textoCifrado, iv)

        // Recuperar
        val record = VelRepository.getEncryptedItem(itemId)
        assertNotNull("El registro debe existir localmente", record)
        assertEquals(itemId, record?.id)
        assertEquals(textoCifrado, record?.encryptedData)
        assertEquals(iv, record?.iv)

        // Eliminar
        VelRepository.deleteItem(itemId)
        val deleted = VelRepository.getEncryptedItem(itemId)
        assertNull("El registro debe haber sido eliminado", deleted)
    }

    @Test
    fun testListEncryptedItems() {
        VelRepository.saveEncryptedItem("nota-1", "D1", "IV1")
        VelRepository.saveEncryptedItem("nota-2", "D2", "IV2")

        val all = VelRepository.getAllEncryptedItems()
        assertNotNull(all)

        // Limpiar
        VelRepository.deleteItem("nota-1")
        VelRepository.deleteItem("nota-2")
    }
}
