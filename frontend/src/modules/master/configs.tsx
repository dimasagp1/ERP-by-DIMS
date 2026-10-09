import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { LookupSelect } from '../../components/Lookup';
import { errorText, useToast } from '../../components/ui';
import type { Option, ResourceDef, Row } from './types';

const opt = (...pairs: [string, string][]): Option[] => pairs.map(([value, label]) => ({ value, label }));

export const ITEM_TYPES = opt(['RM', 'Bahan baku'], ['PM', 'Bahan kemas'], ['WIP', 'Barang setengah jadi'], ['FG', 'Barang jadi'],
  ['SP', 'Sparepart'], ['ATK', 'ATK & konsumabel'], ['SVC', 'Jasa']);
const APP_CODES = opt(['*', 'Semua aplikasi'], ['PRE', 'PRE · Produksi & Engineering'], ['PRC', 'PRC · Procurement'], ['FIN', 'FIN · Finance & Tax'],
  ['GA', 'GA · General Affairs'], ['HC', 'HC · Human Capital'], ['QMS', 'QMS · Quality'], ['SCM', 'SCM · Supply Chain'],
  ['RND', 'RND · RnD'], ['ESS', 'ESS · Layanan Saya'], ['SYS', 'SYS · Pengaturan Sistem']);
const ACTIVE = { key: 'active', label: 'Status', type: 'active' as const, sortable: false };

// ---------------------------------------------------------------- SYS

const companies: ResourceDef = {
  key: 'companies', title: 'Perusahaan', endpoint: '/sys/companies', table: 'sys.company',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'npwp', label: 'NPWP', mono: true }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'npwp', label: 'NPWP' }, { key: 'address', label: 'Alamat', type: 'textarea' }],
};
const plants: ResourceDef = {
  key: 'plants', title: 'Plant & Site', endpoint: '/sys/plants', table: 'sys.plant',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'companyId', label: 'Perusahaan', lookup: 'companies' }, ACTIVE],
  fields: [{ key: 'companyId', label: 'Perusahaan', type: 'lookup', lookup: 'companies', required: true },
    { key: 'code', label: 'Kode plant', required: true, immutable: true, help: 'Dipakai di nomor dokumen, mis. PO/P1/2610/00042' },
    { key: 'name', label: 'Nama', required: true }, { key: 'address', label: 'Alamat', type: 'textarea' }],
};
const departments: ResourceDef = {
  key: 'departments', title: 'Departemen & Seksi', endpoint: '/sys/departments', table: 'sys.department',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'parentId', label: 'Induk', lookup: 'departments' },
    { key: 'appCode', label: 'Aplikasi', mono: true }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'parentId', label: 'Departemen induk', type: 'lookup', lookup: 'departments' },
    { key: 'appCode', label: 'Aplikasi departemen', type: 'select', options: APP_CODES.filter((o) => o.value !== '*') }],
};
const costCenters: ResourceDef = {
  key: 'cost-centers', title: 'Cost Center', endpoint: '/sys/cost-centers', table: 'sys.cost_center',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'departmentId', label: 'Departemen', lookup: 'departments' },
    { key: 'plantId', label: 'Plant', lookup: 'plants' }, { key: 'production', label: 'Produksi', type: 'bool' }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'departmentId', label: 'Departemen', type: 'lookup', lookup: 'departments', required: true },
    { key: 'plantId', label: 'Plant', type: 'lookup', lookup: 'plants' },
    { key: 'production', label: 'Cost center produksi (menerima alokasi overhead)', type: 'bool' }],
};

function UserRolesTab({ row }: { row: Row }) {
  const toast = useToast();
  const roles = useQuery({ queryKey: ['user-roles', row.id], queryFn: () => api.get<{ roleId: number; appCode: string; plantId: number | null }[]>(`/sys/users/${row.id}/roles`) });
  const [list, setList] = useState<{ roleId: number | null; appCode: string; plantId: number | null }[]>([]);
  useEffect(() => { if (roles.data) setList(roles.data.map((r) => ({ ...r }))); }, [roles.data]);
  const save = async () => {
    try {
      await api.put(`/sys/users/${row.id}/roles`, list.filter((r) => r.roleId != null));
      toast.ok('Peran disimpan; berlaku dalam 1 menit atau saat pengguna login ulang');
      roles.refetch();
    } catch (e) {
      toast.error(e);
    }
  };
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <p className="small muted" style={{ margin: 0 }}>Peran berlaku per aplikasi departemen dan bisa dibatasi per plant (PRD §2). Layanan Saya terbuka untuk semua karyawan.</p>
      {list.map((r, i) => (
        <div key={i} className="grid-form" style={{ gridTemplateColumns: '1.3fr 1.3fr 1fr auto', alignItems: 'end' }}>
          <label className="field"><span>Peran</span>
            <LookupSelect lookup="roles" value={r.roleId} allowClear={false} onChange={(v) => setList((s) => s.map((x, j) => j === i ? { ...x, roleId: v } : x))} />
          </label>
          <label className="field"><span>Aplikasi</span>
            <select value={r.appCode} onChange={(e) => setList((s) => s.map((x, j) => j === i ? { ...x, appCode: e.target.value } : x))}>
              {APP_CODES.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </label>
          <label className="field"><span>Plant</span>
            <LookupSelect lookup="plants" value={r.plantId} placeholder="Semua plant" onChange={(v) => setList((s) => s.map((x, j) => j === i ? { ...x, plantId: v } : x))} />
          </label>
          <button className="btn" type="button" onClick={() => setList((s) => s.filter((_, j) => j !== i))}>Hapus</button>
        </div>
      ))}
      <div className="row">
        <button className="btn" type="button" onClick={() => setList((s) => [...s, { roleId: null, appCode: 'FIN', plantId: null }])}>Tambah peran</button>
        <span className="spacer" />
        <button className="btn btn-dark" type="button" onClick={save}>Simpan peran</button>
      </div>
    </div>
  );
}

function PasswordTab({ row }: { row: Row }) {
  const [pw, setPw] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const toast = useToast();
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12, maxWidth: 360 }}>
      <p className="small muted" style={{ margin: 0 }}>Menetapkan kata sandi awal atau mereset kata sandi. Akun yang terkunci karena salah kata sandi juga dibuka.</p>
      <label className="field"><span className="req">Kata sandi baru (min. 8 karakter)</span>
        <input type="password" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} />
      </label>
      {msg && <div className="alert alert-err">{msg}</div>}
      <div><button className="btn btn-dark" type="button" onClick={async () => {
        try {
          await api.post(`/sys/users/${row.id}/password`, { newPassword: pw });
          setPw('');
          setMsg(null);
          toast.ok('Kata sandi diperbarui');
        } catch (e) {
          setMsg(errorText(e));
        }
      }}>Tetapkan kata sandi</button></div>
    </div>
  );
}

const users: ResourceDef = {
  key: 'users', title: 'Pengguna', endpoint: '/sys/users', table: 'sys.app_user', defaultSort: 'username',
  titleOf: (r) => `${r.username} · ${r.fullName}`,
  columns: [{ key: 'username', label: 'Nama pengguna', mono: true }, { key: 'fullName', label: 'Nama lengkap' },
    { key: 'employeeId', label: 'Karyawan', lookup: 'employees' }, { key: 'defaultPlantId', label: 'Plant default', lookup: 'plants' },
    { key: 'lastLoginAt', label: 'Login terakhir', type: 'date', sortable: false }, ACTIVE],
  fields: [{ key: 'username', label: 'Nama pengguna', required: true, immutable: true, help: 'Huruf kecil, angka, titik. Setelah dibuat, tetapkan kata sandi di tab Kata sandi.' },
    { key: 'fullName', label: 'Nama lengkap', required: true }, { key: 'email', label: 'Email', type: 'email' },
    { key: 'employeeId', label: 'Karyawan (HC-03)', type: 'lookup', lookup: 'employees', help: 'Dasar atasan langsung untuk approval' },
    { key: 'defaultPlantId', label: 'Plant default', type: 'lookup', lookup: 'plants' }],
  extraTabs: [
    { key: 'roles', label: 'Peran & akses', render: (r) => <UserRolesTab row={r} /> },
    { key: 'password', label: 'Kata sandi', render: (r) => <PasswordTab row={r} /> },
  ],
};
const roles: ResourceDef = {
  key: 'roles', title: 'Peran', endpoint: '/sys/roles', table: 'sys.role',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'viewScope', label: 'Cakupan lihat' },
    { key: 'actions', label: 'Aksi' }, { key: 'builtin', label: 'Bawaan', type: 'bool' }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'viewScope', label: 'Cakupan lihat', type: 'select', required: true,
      options: opt(['OWN', 'Dokumen sendiri'], ['SECTION', 'Seksinya'], ['DEPARTMENT', 'Departemennya'], ['ALL', 'Semua']) },
    { key: 'actions', label: 'Aksi (dipisah koma)', required: true, full: true,
      help: 'VIEW, CREATE, EDIT, SUBMIT, APPROVE, POST, CANCEL, EXPORT, RELEASE, AUDIT, ADMIN, PAYROLL' },
    { key: 'description', label: 'Keterangan', type: 'textarea' }],
};
const approvalRules: ResourceDef = {
  key: 'approval-rules', title: 'Matriks Approval', endpoint: '/sys/approval-rules', table: 'sys.approval_rule', defaultSort: 'docTypeCode',
  titleOf: (r) => `${r.docTypeCode} · level ${r.level}`,
  columns: [{ key: 'docTypeCode', label: 'Dokumen', mono: true }, { key: 'level', label: 'Level', type: 'number' },
    { key: 'minAmount', label: 'Mulai nilai', type: 'money' }, { key: 'approverType', label: 'Tipe approver' },
    { key: 'label', label: 'Approver' }, { key: 'plantId', label: 'Plant', lookup: 'plants' }, ACTIVE],
  fields: [{ key: 'docTypeCode', label: 'Kode jenis dokumen', required: true, help: 'Sesuai SYS-05, mis. PR, PO, JV' },
    { key: 'level', label: 'Level (1–5)', type: 'number', required: true },
    { key: 'minAmount', label: 'Berlaku mulai nilai (Rp)', type: 'money', required: true },
    { key: 'approverType', label: 'Tipe approver', type: 'select', required: true,
      options: opt(['DIRECT_SUPERIOR', 'Atasan langsung pembuat (HC-02)'], ['ROLE', 'Pemegang peran'], ['USER', 'Pengguna tertentu']) },
    { key: 'approverRole', label: 'Peran (tipe peran)', type: 'select',
      options: opt(['SUPERVISOR', 'Supervisor'], ['MANAGER', 'Manager'], ['DIRECTOR', 'Direktur'], ['QA_RELEASE', 'QA Release Officer']) },
    { key: 'approverApp', label: 'Di aplikasi', type: 'select', options: APP_CODES },
    { key: 'approverUserId', label: 'Pengguna (tipe pengguna)', type: 'lookup', lookup: 'users' },
    { key: 'plantId', label: 'Khusus plant', type: 'lookup', lookup: 'plants' },
    { key: 'label', label: 'Label approver', required: true, full: true }],
  defaults: { level: 1, minAmount: 0, approverType: 'DIRECT_SUPERIOR' },
};
const docTypes: ResourceDef = {
  key: 'doc-types', title: 'Jenis & Penomoran Dokumen', endpoint: '/sys/doc-types', table: 'sys.doc_type',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Dokumen' }, { key: 'prefix', label: 'Prefix', mono: true },
    { key: 'appCode', label: 'Aplikasi', mono: true }, { key: 'menuCode', label: 'Menu', mono: true },
    { key: 'resetPeriod', label: 'Reset', type: 'select', options: opt(['MONTHLY', 'Bulanan'], ['YEARLY', 'Tahunan'], ['NEVER', 'Tidak pernah']) },
    { key: 'requiresEsign', label: 'TTE', type: 'bool' }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'prefix', label: 'Prefix nomor', required: true, help: 'Format: PREFIX/PLANT/YYMM/00001. Nomor tidak pernah dipakai ulang.' },
    { key: 'appCode', label: 'Aplikasi', type: 'select', options: APP_CODES.filter((o) => o.value !== '*'), required: true, immutable: true },
    { key: 'menuCode', label: 'Kode menu', required: true },
    { key: 'resetPeriod', label: 'Reset urutan', type: 'select', required: true, options: opt(['MONTHLY', 'Bulanan'], ['YEARLY', 'Tahunan'], ['NEVER', 'Tidak pernah']) },
    { key: 'requiresEsign', label: 'Wajib tanda tangan elektronik saat approve/posting (catatan GMP)', type: 'bool' }],
  defaults: { resetPeriod: 'MONTHLY' },
};
const items: ResourceDef = {
  key: 'items', title: 'Item Master', endpoint: '/sys/items', table: 'sys.item',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'type', label: 'Jenis', type: 'select', options: ITEM_TYPES },
    { key: 'uomId', label: 'Satuan', lookup: 'uoms' }, { key: 'lotTracked', label: 'Per lot', type: 'bool' },
    { key: 'shelfLifeDays', label: 'Masa simpan (hari)', type: 'number' },
    { key: 'status', label: 'Status item', type: 'select', options: opt(['DEVELOPMENT', 'Development'], ['ACTIVE', 'Aktif'], ['BLOCKED', 'Diblokir'], ['OBSOLETE', 'Usang']) }, ACTIVE],
  filters: [{ key: 'type', label: 'Jenis', options: ITEM_TYPES }],
  fields: [{ key: 'code', label: 'Kode item', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'type', label: 'Jenis', type: 'select', options: ITEM_TYPES, required: true, immutable: true },
    { key: 'uomId', label: 'Satuan dasar', type: 'lookup', lookup: 'uoms', required: true },
    { key: 'category', label: 'Kategori' },
    { key: 'shelfLifeDays', label: 'Masa simpan (hari)', type: 'number' },
    { key: 'storageClass', label: 'Kelas penyimpanan', type: 'select', options: opt(['AMBIENT', 'Ambient'], ['COOL', 'Sejuk'], ['COLD', 'Dingin'], ['B3', 'B3']) },
    { key: 'status', label: 'Status', type: 'select', required: true, options: opt(['DEVELOPMENT', 'Development (hanya trial)'], ['ACTIVE', 'Aktif'], ['BLOCKED', 'Diblokir'], ['OBSOLETE', 'Usang']) },
    { key: 'lotTracked', label: 'Dilacak per lot (wajib untuk bahan, WIP, barang jadi)', type: 'bool' },
    { key: 'halalCritical', label: 'Bahan kritis halal (QMS-13)', type: 'bool' },
    { key: 'description', label: 'Keterangan', type: 'textarea' }],
  defaults: { type: 'RM', lotTracked: true, status: 'ACTIVE', storageClass: 'AMBIENT' },
};
const uoms: ResourceDef = {
  key: 'uoms', title: 'Satuan', endpoint: '/sys/uoms', table: 'sys.uom',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'category', label: 'Kategori' }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'category', label: 'Kategori', type: 'select', required: true, options: opt(['MASS', 'Massa'], ['VOLUME', 'Volume'], ['COUNT', 'Jumlah'], ['LENGTH', 'Panjang'], ['TIME', 'Waktu']) }],
};
const uomConversions: ResourceDef = {
  key: 'uom-conversions', title: 'Konversi', endpoint: '/sys/uom-conversions', table: 'sys.uom_conversion',
  titleOf: () => 'Konversi satuan',
  columns: [{ key: 'fromUomId', label: 'Dari', lookup: 'uoms' }, { key: 'toUomId', label: 'Ke', lookup: 'uoms' },
    { key: 'factor', label: 'Faktor', type: 'number' }, { key: 'itemId', label: 'Khusus item', lookup: 'items' }, ACTIVE],
  fields: [{ key: 'fromUomId', label: 'Dari satuan', type: 'lookup', lookup: 'uoms', required: true },
    { key: 'toUomId', label: 'Ke satuan', type: 'lookup', lookup: 'uoms', required: true },
    { key: 'factor', label: '1 satuan asal = … satuan tujuan', type: 'number', required: true },
    { key: 'itemId', label: 'Khusus item (kosong = umum)', type: 'lookup', lookup: 'items' }],
};
const PARTNER_TYPES = opt(['SUPPLIER', 'Supplier'], ['CUSTOMER', 'Customer'], ['BOTH', 'Supplier & customer'], ['EXPEDITION', 'Ekspedisi']);
const partners: ResourceDef = {
  key: 'partners', title: 'Mitra Bisnis', endpoint: '/sys/partners', table: 'sys.partner',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'type', label: 'Jenis', type: 'select', options: PARTNER_TYPES },
    { key: 'city', label: 'Kota' }, { key: 'paymentTermDays', label: 'Termin (hari)', type: 'number' }, { key: 'creditLimit', label: 'Limit kredit', type: 'money' }, ACTIVE],
  filters: [{ key: 'type', label: 'Jenis', options: PARTNER_TYPES }],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'type', label: 'Jenis', type: 'select', options: PARTNER_TYPES, required: true },
    { key: 'npwp', label: 'NPWP (15/16 digit)' }, { key: 'city', label: 'Kota' }, { key: 'phone', label: 'Telepon' },
    { key: 'email', label: 'Email', type: 'email' }, { key: 'currencyCode', label: 'Mata uang', type: 'select', options: opt(['IDR', 'IDR'], ['USD', 'USD'], ['EUR', 'EUR'], ['CNY', 'CNY']) },
    { key: 'paymentTermDays', label: 'Termin pembayaran (hari)', type: 'number' },
    { key: 'creditLimit', label: 'Limit kredit customer (Rp)', type: 'money', help: 'Dicek FIN-22 sebelum pengiriman' },
    { key: 'address', label: 'Alamat', type: 'textarea' }],
  defaults: { type: 'SUPPLIER', currencyCode: 'IDR', paymentTermDays: 30 },
};
const warehouses: ResourceDef = {
  key: 'warehouses', title: 'Gudang', endpoint: '/sys/warehouses', table: 'sys.warehouse',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'plantId', label: 'Plant', lookup: 'plants' }, { key: 'type', label: 'Jenis' }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'plantId', label: 'Plant', type: 'lookup', lookup: 'plants', required: true },
    { key: 'type', label: 'Jenis', type: 'select', required: true, options: opt(['RM', 'Bahan baku'], ['PM', 'Bahan kemas'], ['FG', 'Barang jadi'], ['SP', 'Sparepart'], ['GENERAL', 'Umum'], ['TRANSIT', 'Transit']) }],
};
const locations: ResourceDef = {
  key: 'locations', title: 'Lokasi Bin', endpoint: '/sys/locations', table: 'sys.location', titleOf: (r) => String(r.binCode),
  columns: [{ key: 'warehouseId', label: 'Gudang', lookup: 'warehouses' }, { key: 'binCode', label: 'Bin', mono: true }, { key: 'zone', label: 'Zona' },
    { key: 'tempClass', label: 'Suhu' }, { key: 'quarantine', label: 'Karantina', type: 'bool' }, { key: 'b3', label: 'B3', type: 'bool' }, ACTIVE],
  fields: [{ key: 'warehouseId', label: 'Gudang', type: 'lookup', lookup: 'warehouses', required: true }, { key: 'binCode', label: 'Kode bin', required: true },
    { key: 'zone', label: 'Zona' }, { key: 'tempClass', label: 'Kelas suhu', type: 'select', required: true, options: opt(['AMBIENT', 'Ambient'], ['COOL', 'Sejuk'], ['COLD', 'Dingin']) },
    { key: 'quarantine', label: 'Lokasi karantina (stok belum Released)', type: 'bool' }, { key: 'b3', label: 'Area B3', type: 'bool' }],
  defaults: { tempClass: 'AMBIENT' },
};
const holidays: ResourceDef = {
  key: 'holidays', title: 'Hari Libur', endpoint: '/sys/holidays', table: 'sys.holiday', defaultSort: 'date', titleOf: (r) => String(r.name),
  columns: [{ key: 'date', label: 'Tanggal', type: 'date', mono: true }, { key: 'name', label: 'Keterangan' }, { key: 'plantId', label: 'Plant', lookup: 'plants' }, ACTIVE],
  fields: [{ key: 'date', label: 'Tanggal', type: 'date', required: true }, { key: 'name', label: 'Keterangan', required: true },
    { key: 'plantId', label: 'Khusus plant (kosong = semua)', type: 'lookup', lookup: 'plants' }],
};
const shifts: ResourceDef = {
  key: 'shifts', title: 'Shift', endpoint: '/sys/shifts', table: 'sys.shift',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'startTime', label: 'Mulai', mono: true }, { key: 'endTime', label: 'Selesai', mono: true }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'startTime', label: 'Mulai', type: 'time', required: true }, { key: 'endTime', label: 'Selesai', type: 'time', required: true },
    { key: 'plantId', label: 'Plant', type: 'lookup', lookup: 'plants' }],
};
const currencies: ResourceDef = {
  key: 'currencies', title: 'Mata Uang', endpoint: '/sys/currencies', table: 'sys.currency',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'symbol', label: 'Simbol' }, { key: 'decimals', label: 'Desimal', type: 'number' }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode ISO', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'symbol', label: 'Simbol' }, { key: 'decimals', label: 'Desimal', type: 'number' }],
};
const rates: ResourceDef = {
  key: 'exchange-rates', title: 'Kurs', endpoint: '/sys/exchange-rates', table: 'sys.exchange_rate', defaultSort: '-rateDate',
  titleOf: (r) => `${r.currencyCode} · ${r.rateDate}`,
  columns: [{ key: 'currencyCode', label: 'Mata uang', mono: true }, { key: 'rateDate', label: 'Tanggal', type: 'date' },
    { key: 'rate', label: 'Kurs transaksi', type: 'number' }, { key: 'taxRate', label: 'Kurs pajak (KMK)', type: 'number' }],
  fields: [{ key: 'currencyCode', label: 'Mata uang', type: 'select', required: true, options: opt(['USD', 'USD'], ['EUR', 'EUR'], ['CNY', 'CNY']) },
    { key: 'rateDate', label: 'Tanggal', type: 'date', required: true }, { key: 'rate', label: 'Kurs transaksi (Rp)', type: 'number', required: true },
    { key: 'taxRate', label: 'Kurs pajak (Rp)', type: 'number' }],
};
const taxCodes: ResourceDef = {
  key: 'tax-codes', title: 'Kode Pajak', endpoint: '/sys/tax-codes', table: 'sys.tax_code',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'type', label: 'Jenis' }, { key: 'rate', label: 'Tarif %', type: 'number' }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'type', label: 'Jenis', type: 'select', required: true, options: opt(['PPN', 'PPN'], ['PPH21', 'PPh 21'], ['PPH22', 'PPh 22'], ['PPH23', 'PPh 23'], ['PPH4_2', 'PPh 4(2)']) },
    { key: 'rate', label: 'Tarif (%)', type: 'number', required: true }],
};
const templates: ResourceDef = {
  key: 'templates', title: 'Template Notifikasi', endpoint: '/sys/notification-templates', table: 'sys.notification_template',
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama' }, { key: 'channel', label: 'Kanal' }, { key: 'subject', label: 'Subjek' }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'channel', label: 'Kanal', type: 'select', required: true, options: opt(['BELL', 'Lonceng'], ['EMAIL', 'Email'], ['PRINT', 'Cetak']) },
    { key: 'subject', label: 'Subjek', full: true }, { key: 'body', label: 'Isi (variabel: {docNo}, {docType}, {level}, {reason})', type: 'textarea', required: true }],
};

// ---------------------------------------------------------------- HC

const positions: ResourceDef = {
  key: 'positions', title: 'Posisi', endpoint: '/hc/positions', table: 'hc.position', titleOf: (r) => `${r.code} · ${r.title}`,
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'title', label: 'Jabatan' }, { key: 'departmentId', label: 'Departemen', lookup: 'departments' },
    { key: 'reportsToId', label: 'Atasan langsung', lookup: 'positions' }, { key: 'grade', label: 'Grade' }, ACTIVE],
  fields: [{ key: 'code', label: 'Kode', required: true, immutable: true }, { key: 'title', label: 'Jabatan', required: true },
    { key: 'departmentId', label: 'Departemen', type: 'lookup', lookup: 'departments', required: true },
    { key: 'reportsToId', label: 'Melapor ke (atasan langsung)', type: 'lookup', lookup: 'positions', help: 'Dasar approval "Atasan langsung" (PRD HC aturan 1)' },
    { key: 'grade', label: 'Grade' }],
};
const employees: ResourceDef = {
  key: 'employees', title: 'Karyawan', endpoint: '/hc/employees', table: 'hc.employee', defaultSort: 'nik', titleOf: (r) => `${r.nik} · ${r.name}`,
  columns: [{ key: 'nik', label: 'NIK', mono: true }, { key: 'name', label: 'Nama' }, { key: 'positionId', label: 'Posisi', lookup: 'positions' },
    { key: 'departmentId', label: 'Departemen', lookup: 'departments' }, { key: 'costCenterId', label: 'Cost center', lookup: 'cost-centers' },
    { key: 'joinDate', label: 'Masuk', type: 'date' }, { key: 'status', label: 'Status' }],
  fields: [{ key: 'nik', label: 'NIK', required: true, immutable: true }, { key: 'name', label: 'Nama', required: true },
    { key: 'email', label: 'Email', type: 'email' }, { key: 'phone', label: 'Telepon' },
    { key: 'positionId', label: 'Posisi', type: 'lookup', lookup: 'positions' },
    { key: 'departmentId', label: 'Departemen', type: 'lookup', lookup: 'departments' },
    { key: 'costCenterId', label: 'Cost center', type: 'lookup', lookup: 'cost-centers' },
    { key: 'plantId', label: 'Plant', type: 'lookup', lookup: 'plants' },
    { key: 'joinDate', label: 'Tanggal masuk', type: 'date', required: true }, { key: 'endDate', label: 'Tanggal keluar', type: 'date' },
    { key: 'employment', label: 'Status kepegawaian', type: 'select', required: true, options: opt(['PKWTT', 'PKWTT (tetap)'], ['PKWT', 'PKWT (kontrak)'], ['OS', 'Outsourcing']) },
    { key: 'status', label: 'Status', type: 'select', required: true, options: opt(['ACTIVE', 'Aktif'], ['RESIGNED', 'Resign'], ['TERMINATED', 'Diberhentikan']) },
    { key: 'delegateId', label: 'Pengganti approval saat cuti', type: 'lookup', lookup: 'employees' },
    { key: 'delegateUntil', label: 'Delegasi sampai', type: 'date' }],
  defaults: { employment: 'PKWTT', status: 'ACTIVE' },
};

// ---------------------------------------------------------------- FIN

const ACCOUNT_TYPES = opt(['ASSET', 'Aset'], ['LIABILITY', 'Liabilitas'], ['EQUITY', 'Ekuitas'], ['REVENUE', 'Pendapatan'], ['EXPENSE', 'Beban']);
const accounts: ResourceDef = {
  key: 'accounts', title: 'Bagan Akun', endpoint: '/fin/accounts', table: 'fin.account', titleOf: (r) => `${r.code} · ${r.name}`,
  columns: [{ key: 'code', label: 'Kode', mono: true }, { key: 'name', label: 'Nama akun' }, { key: 'type', label: 'Jenis', type: 'select', options: ACCOUNT_TYPES },
    { key: 'normalBalance', label: 'Saldo normal' }, { key: 'parentId', label: 'Induk', lookup: 'account-headers' },
    { key: 'postable', label: 'Bisa diposting', type: 'bool' }, { key: 'requiresCostCenter', label: 'Wajib CC', type: 'bool' }, ACTIVE],
  filters: [{ key: 'type', label: 'Jenis', options: ACCOUNT_TYPES }, { key: 'postable', label: 'Posting', options: opt(['true', 'Akun detail'], ['false', 'Akun header']) }],
  fields: [{ key: 'code', label: 'Kode akun', required: true, immutable: true }, { key: 'name', label: 'Nama akun', required: true },
    { key: 'type', label: 'Jenis', type: 'select', required: true, options: ACCOUNT_TYPES },
    { key: 'normalBalance', label: 'Saldo normal', type: 'select', required: true, options: opt(['D', 'Debit'], ['C', 'Kredit']) },
    { key: 'parentId', label: 'Akun induk (header)', type: 'lookup', lookup: 'account-headers' },
    { key: 'postable', label: 'Bisa diposting (bukan akun header)', type: 'bool' },
    { key: 'requiresCostCenter', label: 'Wajib cost center saat dijurnal', type: 'bool' }],
  defaults: { type: 'EXPENSE', normalBalance: 'D', postable: true },
};
const mappings: ResourceDef = {
  key: 'mappings', title: 'Mapping Jurnal Otomatis', endpoint: '/fin/account-mappings', table: 'fin.account_mapping', defaultSort: 'txnType',
  titleOf: (r) => String(r.txnType),
  columns: [{ key: 'txnType', label: 'Transaksi', mono: true }, { key: 'name', label: 'Keterangan' },
    { key: 'debitAccountId', label: 'Debit', lookup: 'accounts' }, { key: 'creditAccountId', label: 'Kredit', lookup: 'accounts' }, ACTIVE],
  fields: [{ key: 'txnType', label: 'Kode transaksi', required: true, immutable: true, help: 'Dipakai modul lain, mis. GR_RM, FG_RECEIPT, PAYROLL' },
    { key: 'name', label: 'Keterangan', required: true },
    { key: 'debitAccountId', label: 'Akun debit', type: 'lookup', lookup: 'accounts', required: true },
    { key: 'creditAccountId', label: 'Akun kredit', type: 'lookup', lookup: 'accounts', required: true }],
};

/** Menu → sumber data master (tab). */
export const MASTER_MENUS: Record<string, ResourceDef[]> = {
  'SYS-01': [companies, plants],
  'SYS-02': [departments, costCenters],
  'SYS-03': [users, roles],
  'SYS-04': [approvalRules],
  'SYS-05': [docTypes],
  'SYS-06': [items],
  'SYS-07': [uoms, uomConversions],
  'SYS-08': [partners],
  'SYS-09': [warehouses, locations],
  'SYS-10': [holidays, shifts],
  'SYS-11': [currencies, rates],
  'SYS-12': [taxCodes],
  'SYS-15': [templates],
  'HC-02': [positions],
  'HC-03': [employees],
  'FIN-02': [accounts, mappings],
};
