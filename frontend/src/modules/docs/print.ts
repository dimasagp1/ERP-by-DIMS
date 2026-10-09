import { formatCell } from './DocList';
import type { Doc, DocConfig } from './types';

const esc = (v: unknown) => String(v ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c] as string);

export interface PrintSpec {
  title: string;
  /** Pasangan label → nilai di kepala dokumen. */
  head: [string, unknown][];
  /** Kolom tabel baris: [label, kunci di baris, jenis format]. */
  columns: [string, string, string?][];
  rows: Doc[];
  totals?: [string, unknown][];
  /** Kolom tanda tangan di kaki dokumen. */
  signatures?: string[];
  note?: string;
}

/** Cetak dokumen di jendela baru (PO, surat jalan, berita acara) dengan tata letak A4 sederhana. */
export function printDocument(docNo: string, spec: PrintSpec) {
  const w = window.open('', '_blank', 'width=900,height=1000');
  if (!w) return;
  const head = spec.head.map(([l, v]) => `<tr><th>${esc(l)}</th><td>${esc(v)}</td></tr>`).join('');
  const cols = spec.columns.map(([l]) => `<th>${esc(l)}</th>`).join('');
  const body = spec.rows.map((r, i) => `<tr><td>${i + 1}</td>${spec.columns.map(([, k, t]) =>
    `<td class="${t === 'money' || t === 'number' ? 'n' : ''}">${esc(formatCell(t, r[k]))}</td>`).join('')}</tr>`).join('');
  const totals = (spec.totals ?? []).map(([l, v]) => `<tr><th>${esc(l)}</th><td class="n">${esc(formatCell('money', v))}</td></tr>`).join('');
  const sig = (spec.signatures ?? []).map((s) => `<div class="sig"><div>${esc(s)}</div><div class="line"></div></div>`).join('');
  w.document.write(`<!doctype html><html lang="id"><head><meta charset="utf-8"><title>${esc(docNo)}</title><style>
    body{font:12px/1.45 "IBM Plex Sans",Arial,sans-serif;color:#111;margin:28px}
    h1{font-size:18px;margin:0 0 2px} .no{font:13px "IBM Plex Mono",monospace;color:#444;margin-bottom:16px}
    table{border-collapse:collapse;width:100%} .head th{text-align:left;font-weight:500;color:#555;width:160px;padding:2px 8px 2px 0;vertical-align:top}
    .lines{margin-top:16px} .lines th,.lines td{border:1px solid #bbb;padding:5px 7px;text-align:left} .lines th{background:#f2f2f2}
    .n{text-align:right;font-family:"IBM Plex Mono",monospace} .tot{width:auto;margin:12px 0 0 auto} .tot th{text-align:right;padding:2px 12px;font-weight:500}
    .sigs{display:flex;gap:24px;margin-top:48px} .sig{flex:1;text-align:center} .sig .line{border-bottom:1px solid #333;height:64px}
    .note{margin-top:16px;color:#444} @media print{body{margin:12mm}}
  </style></head><body>
    <h1>${esc(spec.title)}</h1><div class="no">${esc(docNo)}</div>
    <table class="head">${head}</table>
    <table class="lines"><thead><tr><th>#</th>${cols}</tr></thead><tbody>${body}</tbody></table>
    ${totals ? `<table class="tot">${totals}</table>` : ''}
    ${spec.note ? `<div class="note">${esc(spec.note)}</div>` : ''}
    <div class="sigs">${sig}</div>
    <script>window.onload=()=>{window.print()}</script>
  </body></html>`);
  w.document.close();
}

/** Aksi "Cetak" standar untuk konfigurasi dokumen. */
export function printAction(cfg: Pick<DocConfig, 'title'>, build: (doc: Doc) => PrintSpec, label = 'Cetak') {
  return {
    label,
    show: () => true,
    run: async (doc: Doc) => printDocument(String(doc.docNo ?? cfg.title), build(doc)),
  };
}
