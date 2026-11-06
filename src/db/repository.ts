// src/db/repository.ts
import { velDb, VelLocalRecord } from './db';

export const VelRepository = {
  async saveEncryptedItem(id: string, encryptedData: string, iv: string): Promise<void> {
    const record: VelLocalRecord = {
      id,
      encryptedData,
      iv,
      updatedAt: Date.now(),
    };
    await velDb.save(record);
  },

  async getEncryptedItem(id: string): Promise<VelLocalRecord | undefined> {
    return await velDb.get(id);
  },

  async deleteItem(id: string): Promise<void> {
    await velDb.remove(id);
  },

  async getAllEncryptedItems(): Promise<VelLocalRecord[]> {
    return await velDb.getAll();
  }
};
