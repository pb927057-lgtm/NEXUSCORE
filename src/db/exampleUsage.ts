// src/db/exampleUsage.ts
import { VelRepository } from './repository';

/**
 * Ejemplo de uso dentro de una función de guardado respetando la arquitectura offline-first.
 * Guarda registros cifrados directamente en IndexedDB sin requerir conexión a internet ni servidores.
 */
export const handleSave = async (
  itemId = 'nota-secreta-1',
  textoCifradoSimulado = 'DATOS_CIFRADOS_CON_AES_GCM',
  ivSimulado = 'iv-aleatorio'
) => {
  await VelRepository.saveEncryptedItem(itemId, textoCifradoSimulado, ivSimulado);
  console.log('¡Guardado localmente en IndexedDB sin depender de la nube!');
};
