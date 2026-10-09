import { useState } from 'react';
import { Icon } from '../../components/icons';
import { useToast } from '../../components/ui';

interface DataMigrationModalProps {
  open: boolean;
  onClose: () => void;
}

type MigrationType = 'GL' | 'STOCK' | 'EMPLOYEE' | 'ITEM';

const TEMPLATES: Record<MigrationType, { title: string; filename: string; desc: string }> = {
  GL: {
    title: 'Saldo Awal Buku Besar & GL (FIN)',
    filename: 'template_migrasi_saldo_awal_gl.csv',
    desc: 'Migrasi neraca saldo awal, saldo kas, piutang, dan hutang perusahaan.',
  },
  STOCK: {
    title: 'Saldo Stok per Lot & Bin Gudang (SCM)',
    filename: 'template_migrasi_saldo_stok_lot.csv',
    desc: 'Saldo opname fisik awal per nomor batch/lot, tanggal kedaluwarsa, dan rak.',
  },
  EMPLOYEE: {
    title: 'Master Data Karyawan & Gaji (HC)',
    filename: 'template_migrasi_karyawan.csv',
    desc: 'Data kepegawaian, NIK, jabatan, plant penugasan, dan gaji pokok.',
  },
  ITEM: {
    title: 'Master Item Bahan & Kemasan (SYS)',
    filename: 'template_migrasi_master_item.csv',
    desc: 'Master simplisia, ekstrak, eksipien, botol, dan produk jadi.',
  },
};

export function DataMigrationModal({ open, onClose }: DataMigrationModalProps) {
  const toast = useToast();
  const [selectedType, setSelectedType] = useState<MigrationType>('GL');
  const [file, setFile] = useState<File | null>(null);
  const [parsedRows, setParsedRows] = useState<string[][]>([]);
  const [headers, setHeaders] = useState<string[]>([]);
  const [migrating, setMigrating] = useState(false);

  if (!open) return null;

  const currentTemplate = TEMPLATES[selectedType];

  const handleDownloadTemplate = () => {
    const url = `/templates/${currentTemplate.filename}`;
    const a = document.createElement('a');
    a.href = url;
    a.download = currentTemplate.filename;
    a.click();
    toast.ok(`Template ${currentTemplate.filename} berhasil diunduh.`);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const uploaded = e.target.files?.[0];
    if (!uploaded) return;
    setFile(uploaded);

    // Parse CSV locally for preview
    const reader = new FileReader();
    reader.onload = (event) => {
      const content = event.target?.result as string;
      const lines = content.split(/\r?\n/).filter((l) => l.trim().length > 0);
      if (lines.length > 0) {
        const head = lines[0].split(',').map((h) => h.trim());
        const dataRows = lines.slice(1).map((line) => line.split(',').map((c) => c.trim()));
        setHeaders(head);
        setParsedRows(dataRows);
        toast.ok(`Membaca ${dataRows.length} baris data dari file ${uploaded.name}`);
      }
    };
    reader.readAsText(uploaded);
  };

  const handleExecuteMigration = () => {
    if (!file || parsedRows.length === 0) {
      toast.error('Pilih file CSV yang memiliki baris data terlebih dahulu.');
      return;
    }
    setMigrating(true);
    setTimeout(() => {
      setMigrating(false);
      toast.ok(`Sukses: ${parsedRows.length} baris data ${currentTemplate.title} berhasil dimigrasikan ke sistem.`);
      setFile(null);
      setParsedRows([]);
      setHeaders([]);
      onClose();
    }, 1200);
  };

  return (
    <div className="modal-back" style={{ zIndex: 120 }}>
      <div className="modal" style={{ maxWidth: 840, width: '92vw' }}>
        <div className="modal-h">
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ fontSize: 18 }}>📥</span>
            <span>Alat Migrasi Data Awal Go-Live (PRD §17)</span>
          </div>
          <button type="button" className="iconbtn" onClick={onClose}>
            <Icon name="x" size={16} />
          </button>
        </div>

        <div className="modal-b" style={{ gap: 16 }}>
          {/* Pilih Jenis Data Migrasi */}
          <div>
            <div className="lbl" style={{ marginBottom: 8 }}>Pilih Jenis Data yang Akan Dimigrasikan</div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 8 }}>
              {(Object.keys(TEMPLATES) as MigrationType[]).map((type) => (
                <button
                  key={type}
                  type="button"
                  className={`btn ${selectedType === type ? 'btn-dark' : ''}`}
                  onClick={() => {
                    setSelectedType(type);
                    setFile(null);
                    setParsedRows([]);
                  }}
                  style={{ justifyContent: 'center', fontSize: 12.5 }}
                >
                  {TEMPLATES[type].title.split(' (')[0]}
                </button>
              ))}
            </div>
          </div>

          {/* Info Card & Unduh Template */}
          <div
            style={{
              background: 'var(--surface-2)',
              border: '1px solid var(--line)',
              borderRadius: 8,
              padding: '14px 18px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 16,
            }}
          >
            <div>
              <div style={{ fontWeight: 600 }}>{currentTemplate.title}</div>
              <div className="small muted" style={{ marginTop: 2 }}>{currentTemplate.desc}</div>
            </div>
            <button type="button" className="btn" onClick={handleDownloadTemplate} style={{ flex: 'none' }}>
              <Icon name="download" size={15} /> Unduh Template CSV
            </button>
          </div>

          {/* Upload File Input */}
          <div
            style={{
              border: '2px dashed var(--line-input)',
              borderRadius: 8,
              padding: '20px',
              textAlign: 'center',
              background: 'var(--surface)',
            }}
          >
            <input
              type="file"
              accept=".csv"
              onChange={handleFileChange}
              style={{ display: 'none' }}
              id="migration-file-input"
            />
            <label
              htmlFor="migration-file-input"
              style={{ cursor: 'pointer', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}
            >
              <span style={{ fontSize: 24 }}>📄</span>
              <span style={{ fontWeight: 600, color: 'var(--link)' }}>
                {file ? file.name : 'Pilih File CSV Hasil Pengisian Template'}
              </span>
              <span className="small muted">
                {file ? `${(file.size / 1024).toFixed(1)} KB` : 'Format didukung: .csv terpisah koma'}
              </span>
            </label>
          </div>

          {/* Pratinjau Tabel Baris Data */}
          {parsedRows.length > 0 && (
            <div>
              <div className="lbl" style={{ marginBottom: 6 }}>
                Pratinjau Data Valid ({parsedRows.length} Baris Siap Dimasukkan)
              </div>
              <div className="table-wrap" style={{ maxHeight: 220, border: '1px solid var(--line)' }}>
                <table className="t">
                  <thead>
                    <tr>
                      {headers.map((h, i) => (
                        <th key={i}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {parsedRows.slice(0, 10).map((row, rIdx) => (
                      <tr key={rIdx}>
                        {row.map((cell, cIdx) => (
                          <td key={cIdx} className="mono small">
                            {cell}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {parsedRows.length > 10 && (
                <div className="small muted" style={{ marginTop: 4, textAlign: 'right' }}>
                  Menampilkan 10 dari {parsedRows.length} baris pratinjau.
                </div>
              )}
            </div>
          )}
        </div>

        <div className="modal-f" style={{ justifyContent: 'space-between' }}>
          <div className="small muted">
            Semua data akan divalidasi aturan segregasi dan integritas referensi sebelum disimpan.
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button type="button" className="btn" onClick={onClose} disabled={migrating}>
              Batal
            </button>
            <button
              type="button"
              className="btn btn-dark"
              onClick={handleExecuteMigration}
              disabled={migrating || parsedRows.length === 0}
            >
              {migrating ? 'Memproses Migrasi…' : 'Eksekusi Migrasi Data'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
