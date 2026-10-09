/** Chip status selalu memuat teks, tidak hanya warna (PRD §15.5). */

const MAP: Record<string, { label: string; tone: string }> = {
  DRAFT: { label: 'Draft', tone: 'draft' },
  SUBMITTED: { label: 'Diajukan', tone: 'submitted' },
  APPROVED: { label: 'Disetujui', tone: 'approved' },
  POSTED: { label: 'Diposting', tone: 'posted' },
  DONE: { label: 'Selesai', tone: 'approved' },
  REJECTED: { label: 'Ditolak', tone: 'rejected' },
  CANCELLED: { label: 'Dibatalkan', tone: 'draft' },
  PENDING: { label: 'Menunggu', tone: 'submitted' },
  WAITING: { label: 'Antre', tone: 'draft' },
  QUARANTINE: { label: 'Quarantine', tone: 'warn' },
  RELEASED: { label: 'Released', tone: 'approved' },
  HOLD: { label: 'Hold', tone: 'hold' },
  EXPIRED: { label: 'Kedaluwarsa', tone: 'posted' },
  SUCCESS: { label: 'Berhasil', tone: 'approved' },
  FAILED: { label: 'Gagal', tone: 'rejected' },
  ACTIVE: { label: 'Aktif', tone: 'approved' },
  INACTIVE: { label: 'Nonaktif', tone: 'draft' },
  LOCKED: { label: 'Terkunci', tone: 'posted' },
  OPEN: { label: 'Terbuka', tone: 'approved' },
};

export function statusLabel(status: string): string {
  return MAP[status]?.label ?? status;
}

export function StatusChip({ status, label }: { status: string; label?: string }) {
  const m = MAP[status] ?? { label: status, tone: 'draft' };
  const striped = status === 'CANCELLED';
  return (
    <span
      className="chip"
      style={{
        background: striped
          ? 'repeating-linear-gradient(135deg, var(--chip-draft-bg), var(--chip-draft-bg) 4px, transparent 4px, transparent 7px)'
          : `var(--chip-${m.tone}-bg)`,
        color: `var(--chip-${m.tone}-fg)`,
        border: striped ? '1px solid var(--line-input)' : undefined,
      }}
    >
      {label ?? m.label}
    </span>
  );
}
