export function RndDashboardPage() {
  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 20 }}>
        <h2>Dashboard Riset & Pengembangan (R&D)</h2>
        <p className="muted">Monitoring proyek baru, formula aktif, trial lab, dan registrasi produk.</p>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16, marginBottom: 24 }}>
        <div className="card" style={{ padding: 16 }}>
          <div className="lbl">Proyek Aktif</div>
          <div style={{ fontSize: 28, fontWeight: 700, marginTop: 4 }}>8</div>
          <div className="small muted">3 dalam tahap formula</div>
        </div>
        <div className="card" style={{ padding: 16 }}>
          <div className="lbl">Trial Berjalan Bulan Ini</div>
          <div style={{ fontSize: 28, fontWeight: 700, marginTop: 4, color: 'var(--pre-accent, #9a3412)' }}>14</div>
          <div className="small muted">85% tingkat keberhasilan</div>
        </div>
        <div className="card" style={{ padding: 16 }}>
          <div className="lbl">Registrasi BPOM Berjalan</div>
          <div style={{ fontSize: 28, fontWeight: 700, marginTop: 4, color: 'var(--qms-accent, #047857)' }}>5</div>
          <div className="small muted">2 izin terbit bulan lalu</div>
        </div>
        <div className="card" style={{ padding: 16 }}>
          <div className="lbl">Artwork Disetujui</div>
          <div style={{ fontSize: 28, fontWeight: 700, marginTop: 4 }}>12</div>
          <div className="small muted">Siap cetak kemasan komersial</div>
        </div>
      </div>
    </div>
  );
}

export function TrialSampleReqPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Permintaan Bahan Trial & Sampel (RND-05)</h3>
      <p className="muted">Permintaan bahan baku skala kecil untuk pengujian formula di laboratorium atau pilot plant.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <p>Bahan trial diambil dari gudang sampel atau diajukan sebagai Purchase Requisition (PRC-02) dengan peruntukan R&D.</p>
      </div>
    </div>
  );
}

export function IngredientBankPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Bank Data Bahan Baku & Simplisia (RND-13)</h3>
      <p className="muted">Pangkalan data monografi simplisia, ekstrak terstandar, CoA pemasok, dan profil fitokimia.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <table className="t" style={{ width: '100%' }}>
          <thead>
            <tr>
              <th>Kode Bahan</th>
              <th>Nama Simplisia / Ekstrak</th>
              <th>Nama Latin / Spesies</th>
              <th>Kandungan Aktif Utama</th>
              <th>Status Monografi</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td className="mono">RM-TEM-01</td>
              <td>Ekstrak Temulawak</td>
              <td><em>Curcuma xanthorrhiza</em></td>
              <td>Kurkuminoid ≥ 20%, Xanthorrhizol</td>
              <td><span className="chip" style={{ background: '#dcfce7', color: '#166534' }}>Terverifikasi</span></td>
            </tr>
            <tr>
              <td className="mono">RM-JAH-01</td>
              <td>Ekstrak Jahe Merah</td>
              <td><em>Zingiber officinale var. rubrum</em></td>
              <td>Gingerol ≥ 5%, Shogaol</td>
              <td><span className="chip" style={{ background: '#dcfce7', color: '#166534' }}>Terverifikasi</span></td>
            </tr>
            <tr>
              <td className="mono">RM-SMN-01</td>
              <td>Ekstrak Meniran</td>
              <td><em>Phyllanthus niruri</em></td>
              <td>Filantin & Hipofilantin</td>
              <td><span className="chip" style={{ background: '#dcfce7', color: '#166534' }}>Terverifikasi</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function FormulaChangeReqPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Usulan Perubahan Formula / Proses (RND-14)</h3>
      <p className="muted">Inisiasi change control terintegrasi dengan penjaminan mutu (QMS-02) sebelum reformulasi disahkan.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <p>Setiap perubahan bahan baku, rasio komposisi, atau parameter mesin wajib melalui kajian risiko mutu dan uji stabilitas komparatif.</p>
      </div>
    </div>
  );
}

export function RndReportPage() {
  return (
    <div style={{ padding: 20 }}>
      <h3>Laporan & Kinerja RnD (RND-90)</h3>
      <p className="muted">Analisis cycle time pengembangan, tingkat kelolosan uji klinis/stabilitas, dan biaya R&D.</p>
      <div className="card" style={{ padding: 16, marginTop: 16 }}>
        <div style={{ display: 'flex', gap: 24 }}>
          <div>
            <div className="lbl">Rata-rata Waktu Idea-to-Launch</div>
            <div style={{ fontSize: 24, fontWeight: 700 }}>7.2 Bulan</div>
          </div>
          <div>
            <div className="lbl">Tingkat Kelulusan Registrasi BPOM</div>
            <div style={{ fontSize: 24, fontWeight: 700, color: 'var(--qms-accent, #047857)' }}>100%</div>
          </div>
          <div>
            <div className="lbl">Efisiensi Biaya Formula Baru vs Target</div>
            <div style={{ fontSize: 24, fontWeight: 700, color: 'var(--pre-accent, #9a3412)' }}>+4.8%</div>
          </div>
        </div>
      </div>
    </div>
  );
}
