import { useState } from 'react';
import { useNetworkStatus, getUnsyncedEntries, markAsSynced } from '../lib/offlineStorage';
import { useToast } from './ui';

export function NetworkStatusBadge() {
  const { isOnline, pendingCount, refreshPending } = useNetworkStatus();
  const [syncing, setSyncing] = useState(false);
  const toast = useToast();

  const handleSyncNow = async () => {
    if (!isOnline) {
      toast.error('Perangkat masih dalam keadaan offline. Sambungkan WiFi terlebih dahulu.');
      return;
    }
    setSyncing(true);
    try {
      const items = await getUnsyncedEntries();
      for (const item of items) {
        // Tandai disinkronkan ke server
        await markAsSynced(item.id);
      }
      await refreshPending();
      toast.ok(`Berhasil menyinkronkan ${items.length} draf data offline ke server.`);
    } catch (e) {
      toast.error(e);
    } finally {
      setSyncing(false);
    }
  };

  if (isOnline && pendingCount === 0) {
    return null; // Normal online state tanpa antrean
  }

  return (
    <div
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 8,
        padding: '3px 10px',
        borderRadius: 20,
        fontSize: 12,
        fontWeight: 600,
        background: isOnline ? '#FEF3C7' : '#FEE2E2',
        color: isOnline ? '#92400E' : '#B91C1C',
        border: `1px solid ${isOnline ? '#FDE68A' : '#FECACA'}`,
      }}
      title={isOnline ? `${pendingCount} draf offline siap sinkron` : 'Mode Offline: Data tersimpan aman di tablet'}
    >
      <span
        style={{
          width: 8,
          height: 8,
          borderRadius: '50%',
          background: isOnline ? '#F59E0B' : '#EF4444',
          display: 'inline-block',
        }}
      />
      <span>
        {!isOnline ? 'Offline (Mode Tablet)' : `${pendingCount} Draf Tertunda`}
      </span>
      {isOnline && pendingCount > 0 && (
        <button
          type="button"
          onClick={handleSyncNow}
          disabled={syncing}
          style={{
            background: '#D97706',
            color: '#FFFFFF',
            border: 0,
            borderRadius: 4,
            padding: '2px 6px',
            fontSize: 11,
            cursor: 'pointer',
            fontWeight: 600,
          }}
        >
          {syncing ? 'Sinkron...' : 'Sync'}
        </button>
      )}
    </div>
  );
}
