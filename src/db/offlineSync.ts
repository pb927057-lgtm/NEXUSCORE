// src/db/offlineSync.ts
import { velDb, VelLocalRecord } from './db';

/**
 * Gestor de sincronización Offline-First con cifrado en reposo para VEL.
 * Garantiza la soberanía local de los datos antes de cualquier transmisión de red.
 */

// Clave local derivada para almacenamiento en reposo (AES-256-GCM)
let cachedCryptoKey: CryptoKey | null = null;

async function getOrCreateLocalKey(): Promise<CryptoKey> {
  if (cachedCryptoKey) return cachedCryptoKey;

  // En un entorno de producción, esta clave se deriva del Keystore/SecureEnclave o PIN del usuario.
  // Aquí usamos una clave persistente local o generamos una derivada reproducible.
  const storedKeyMaterial = localStorage.getItem('vel_local_key_seed');
  let rawKey: Uint8Array;

  if (storedKeyMaterial) {
    rawKey = new Uint8Array(JSON.parse(storedKeyMaterial));
  } else {
    rawKey = crypto.getRandomValues(new Uint8Array(32));
    localStorage.setItem('vel_local_key_seed', JSON.stringify(Array.from(rawKey)));
  }

  cachedCryptoKey = await crypto.subtle.importKey(
    'raw',
    rawKey as BufferSource,
    { name: 'AES-GCM' },
    false,
    ['encrypt', 'decrypt']
  );

  return cachedCryptoKey;
}

export class VelOfflineSyncManager {
  /**
   * Guarda un registro cifrado localmente en IndexedDB respetando la soberanía de datos (Offline-First).
   */
  async saveLocal(id: string, data: object | string): Promise<VelLocalRecord> {
    const key = await getOrCreateLocalKey();
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const plaintext = typeof data === 'string' ? data : JSON.stringify(data);
    const encoded = new TextEncoder().encode(plaintext);

    const ciphertextBuffer = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv: iv as BufferSource },
      key,
      encoded as BufferSource
    );

    const ciphertextBase64 = btoa(String.fromCharCode(...new Uint8Array(ciphertextBuffer)));
    const ivBase64 = btoa(String.fromCharCode(...iv));

    const record: VelLocalRecord = {
      id,
      encryptedData: ciphertextBase64,
      iv: ivBase64,
      updatedAt: Date.now()
    };

    await velDb.save(record);
    return record;
  }

  /**
   * Lee y descifra un registro de la base de datos local.
   */
  async getLocal<T = any>(id: string): Promise<T | null> {
    const record = await velDb.get(id);
    if (!record) return null;

    try {
      const key = await getOrCreateLocalKey();
      const iv = Uint8Array.from(atob(record.iv), c => c.charCodeAt(0));
      const ciphertext = Uint8Array.from(atob(record.encryptedData), c => c.charCodeAt(0));

      const decryptedBuffer = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: iv as BufferSource },
        key,
        ciphertext as BufferSource
      );

      const text = new TextDecoder().decode(decryptedBuffer);
      try {
        return JSON.parse(text) as T;
      } catch {
        return text as unknown as T;
      }
    } catch (err) {
      console.error('Error al descifrar registro local:', err);
      return null;
    }
  }

  /**
   * Obtiene todos los registros locales (metadatos cifrados).
   */
  async getAllRecords(): Promise<VelLocalRecord[]> {
    return await velDb.getAll();
  }

  /**
   * Elimina un registro local.
   */
  async removeRecord(id: string): Promise<void> {
    await velDb.remove(id);
  }

  /**
   * Cuenta total de registros locales.
   */
  async count(): Promise<number> {
    return await velDb.count();
  }
}

export const velOfflineSync = new VelOfflineSyncManager();
