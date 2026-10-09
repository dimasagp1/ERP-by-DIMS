import { useEffect, useState } from 'react';

export interface OfflineEntry {
  id: string;
  type: 'EBMR' | 'DISPENSING' | 'IPC' | 'LINE_CLEARANCE' | 'OTHER';
  menuCode: string;
  docNo?: string;
  payload: any;
  savedAt: string;
  synced: boolean;
}

const DB_NAME = 'herbatech_offline_db';
const DB_VERSION = 1;
const STORE_NAME = 'offline_entries';

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof window === 'undefined' || !window.indexedDB) {
      reject(new Error('IndexedDB tidak didukung pada browser ini'));
      return;
    }
    const req = window.indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, { keyPath: 'id' });
        store.createIndex('type', 'type', { unique: false });
        store.createIndex('synced', 'synced', { unique: false });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

/** Simpan draft offline ke IndexedDB */
export async function saveOfflineDraft(entry: Omit<OfflineEntry, 'id' | 'savedAt' | 'synced'>): Promise<string> {
  const db = await openDatabase();
  const id = `draft_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
  const record: OfflineEntry = {
    ...entry,
    id,
    savedAt: new Date().toISOString(),
    synced: false,
  };

  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite');
    const store = tx.objectStore(STORE_NAME);
    const req = store.put(record);
    req.onsuccess = () => resolve(id);
    req.onerror = () => reject(req.error);
  });
}

/** Dapatkan semua entri offline yang belum disinkronkan */
export async function getUnsyncedEntries(): Promise<OfflineEntry[]> {
  try {
    const db = await openDatabase();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readonly');
      const store = tx.objectStore(STORE_NAME);
      const req = store.getAll();
      req.onsuccess = () => {
        const all: OfflineEntry[] = req.result || [];
        resolve(all.filter((item) => !item.synced));
      };
      req.onerror = () => reject(req.error);
    });
  } catch {
    return [];
  }
}

/** Tandai entri offline telah berhasil disinkronkan */
export async function markAsSynced(id: string): Promise<void> {
  const db = await openDatabase();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite');
    const store = tx.objectStore(STORE_NAME);
    const req = store.get(id);
    req.onsuccess = () => {
      const record: OfflineEntry = req.result;
      if (record) {
        record.synced = true;
        store.put(record);
      }
      resolve();
    };
    req.onerror = () => reject(req.error);
  });
}

/** Hook status koneksi jaringan online / offline */
export function useNetworkStatus() {
  const [isOnline, setIsOnline] = useState<boolean>(() => (typeof navigator !== 'undefined' ? navigator.onLine : true));
  const [pendingCount, setPendingCount] = useState<number>(0);

  const refreshPending = async () => {
    const items = await getUnsyncedEntries();
    setPendingCount(items.length);
  };

  useEffect(() => {
    const handleOnline = () => {
      setIsOnline(true);
      refreshPending();
    };
    const handleOffline = () => {
      setIsOnline(false);
      refreshPending();
    };

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    refreshPending();

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  return { isOnline, pendingCount, refreshPending };
}
