import type { Option, ResourceDef } from './types';

const opt = (...pairs: [string, string][]): Option[] => pairs.map(([value, label]) => ({ value, label }));
const ACTIVE = { key: 'active', label: 'Status', type: 'active' as const, sortable: false };

export const supplierProfiles: ResourceDef = {
  key: 'supplier-profiles', title: 'Profil & Kualifikasi Supplier', endpoint: '/prc/supplier-profiles', table: 'prc.supplier_profile',
  columns: [
    { key: 'partnerId', label: 'Supplier', lookup: 'partners' },
    { key: 'legalName', label: 'Nama Legal' },
    { key: 'qualificationStatus', label: 'Status Kualifikasi' },
    ACTIVE
  ],
  fields: [
    { key: 'partnerId', label: 'Supplier', type: 'lookup', lookup: 'partners', required: true, immutable: true },
    { key: 'legalName', label: 'Nama Legal' },
    { key: 'nib', label: 'NIB' },
    { key: 'bankName', label: 'Nama Bank' },
    { key: 'bankAccountNo', label: 'No Rekening' },
    { key: 'bankAccountName', label: 'Nama Rekening' },
    { key: 'contactPerson', label: 'Kontak Person' },
    { key: 'halalCertNo', label: 'No Sertifikat Halal' },
    { key: 'halalCertExpiry', label: 'Tgl Kedaluwarsa Halal', type: 'date' },
    { key: 'qualificationStatus', label: 'Status Kualifikasi', type: 'select', required: true, options: opt(['PENDING', 'Pending'], ['QUALIFIED', 'Qualified'], ['CONDITIONAL', 'Conditional'], ['DISQUALIFIED', 'Disqualified']) },
    { key: 'notes', label: 'Catatan', type: 'textarea' }
  ]
};

export const asls: ResourceDef = {
  key: 'asls', title: 'Approved Supplier List (ASL)', endpoint: '/prc/asls', table: 'prc.asl',
  columns: [
    { key: 'itemId', label: 'Item', lookup: 'items' },
    { key: 'partnerId', label: 'Supplier', lookup: 'partners' },
    { key: 'manufacturer', label: 'Pabrikan' },
    { key: 'status', label: 'Status' },
    ACTIVE
  ],
  fields: [
    { key: 'itemId', label: 'Item', type: 'lookup', lookup: 'items', required: true, immutable: true },
    { key: 'partnerId', label: 'Supplier', type: 'lookup', lookup: 'partners', required: true, immutable: true },
    { key: 'manufacturer', label: 'Pabrikan', required: true },
    { key: 'status', label: 'Status', type: 'select', required: true, options: opt(['APPROVED', 'Approved'], ['CONDITIONAL', 'Conditional'], ['BLOCKED', 'Blocked']) },
    { key: 'validUntil', label: 'Berlaku Sampai', type: 'date' },
    { key: 'notes', label: 'Catatan', type: 'textarea' }
  ]
};

export const priceLists: ResourceDef = {
  key: 'price-lists', title: 'Daftar Harga & Kontrak', endpoint: '/prc/price-lists', table: 'prc.price_list',
  columns: [
    { key: 'partnerId', label: 'Supplier', lookup: 'partners' },
    { key: 'itemId', label: 'Item', lookup: 'items' },
    { key: 'price', label: 'Harga', type: 'money' },
    { key: 'currencyCode', label: 'Mata Uang' },
    { key: 'validFrom', label: 'Berlaku Mulai', type: 'date' },
    ACTIVE
  ],
  fields: [
    { key: 'partnerId', label: 'Supplier', type: 'lookup', lookup: 'partners', required: true, immutable: true },
    { key: 'itemId', label: 'Item', type: 'lookup', lookup: 'items', required: true, immutable: true },
    { key: 'contractNo', label: 'No Kontrak' },
    { key: 'price', label: 'Harga', type: 'money', required: true },
    { key: 'currencyCode', label: 'Mata Uang', required: true },
    { key: 'minQty', label: 'Min Qty', type: 'number', required: true },
    { key: 'leadTimeDays', label: 'Lead Time (Hari)', type: 'number', required: true },
    { key: 'validFrom', label: 'Berlaku Mulai', type: 'date', required: true },
    { key: 'validUntil', label: 'Berlaku Sampai', type: 'date' }
  ],
  defaults: { currencyCode: 'IDR', minQty: 0, leadTimeDays: 14 }
};

export const stockParams: ResourceDef = {
  key: 'stock-params', title: 'Parameter Stok', endpoint: '/scm/stock-params', table: 'scm.stock_param',
  columns: [
    { key: 'plantId', label: 'Plant', lookup: 'plants' },
    { key: 'itemId', label: 'Item', lookup: 'items' },
    { key: 'minQty', label: 'Min Qty', type: 'number' },
    { key: 'maxQty', label: 'Max Qty', type: 'number' },
    { key: 'safetyStock', label: 'Safety Stock', type: 'number' },
    ACTIVE
  ],
  fields: [
    { key: 'plantId', label: 'Plant', type: 'lookup', lookup: 'plants', required: true, immutable: true },
    { key: 'itemId', label: 'Item', type: 'lookup', lookup: 'items', required: true, immutable: true },
    { key: 'minQty', label: 'Min Qty', type: 'number', required: true },
    { key: 'maxQty', label: 'Max Qty', type: 'number' },
    { key: 'reorderPoint', label: 'Reorder Point', type: 'number', required: true },
    { key: 'safetyStock', label: 'Safety Stock', type: 'number', required: true },
    { key: 'leadTimeDays', label: 'Lead Time (Hari)', type: 'number', required: true },
    { key: 'moq', label: 'MOQ', type: 'number', required: true },
    { key: 'lotSize', label: 'Lot Size', type: 'number', required: true }
  ],
  defaults: { minQty: 0, reorderPoint: 0, safetyStock: 0, leadTimeDays: 14, moq: 0, lotSize: 0 }
};
