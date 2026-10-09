import { LookupSelect } from '../../components/Lookup';
import type { FieldDef } from './types';

/** Satu kontrol input untuk definisi field master. */
export function FieldInput({ def, value, onChange, disabled, id }: {
  def: FieldDef; value: unknown; onChange: (v: unknown) => void; disabled?: boolean; id: string;
}) {
  switch (def.type ?? 'text') {
    case 'bool':
      return (
        <label className="check" style={{ borderBottom: 0, padding: '6px 0' }}>
          <input id={id} type="checkbox" checked={Boolean(value)} disabled={disabled} onChange={(e) => onChange(e.target.checked)} />
          {def.label}
        </label>
      );
    case 'select':
      return (
        <select id={id} value={(value as string) ?? ''} disabled={disabled} onChange={(e) => onChange(e.target.value || null)}>
          {!def.required && <option value="">—</option>}
          {def.options?.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      );
    case 'lookup':
      return <LookupSelect id={id} lookup={def.lookup!} value={value as number | null} filters={def.lookupFilters}
        disabled={disabled} onChange={(v) => onChange(v)} allowClear={!def.required} />;
    case 'number':
    case 'money':
      return <input id={id} type="number" step="any" value={value == null ? '' : String(value)} disabled={disabled}
        onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))} style={{ textAlign: 'right' }} />;
    case 'date':
      return <input id={id} type="date" value={(value as string) ?? ''} disabled={disabled} onChange={(e) => onChange(e.target.value || null)} />;
    case 'time':
      return <input id={id} type="time" value={((value as string) ?? '').slice(0, 5)} disabled={disabled} onChange={(e) => onChange(e.target.value || null)} />;
    case 'textarea':
      return <textarea id={id} value={(value as string) ?? ''} disabled={disabled} onChange={(e) => onChange(e.target.value)} />;
    default:
      return <input id={id} type={def.type === 'email' ? 'email' : 'text'} value={(value as string) ?? ''} disabled={disabled}
        onChange={(e) => onChange(e.target.value)} />;
  }
}
