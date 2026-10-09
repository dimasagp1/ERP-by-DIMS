import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { LookupSelect } from '../../components/Lookup';
import { useToast } from '../../components/ui';
import { todayIso } from '../../lib/format';
import { MasterPage } from '../master/MasterPage';
import { labor } from '../master/m1';

/** PRE-12: jam kerja per WO, dengan usulan otomatis dari absensi operator WO. */
export function LaborPage() {
  const [wo, setWo] = useState<number | null>(null);
  const [date, setDate] = useState(todayIso());
  const toast = useToast();
  const qc = useQueryClient();
  const fill = async () => {
    try {
      const r = await api.post<{ created: number }>(`/pre/labor/from-attendance?woId=${wo}&date=${date}`);
      toast.ok(r.created ? `${r.created} baris jam kerja dibuat dari absensi` : 'Tidak ada operator WO yang hadir dengan sisa jam pada tanggal itu');
      qc.invalidateQueries({ queryKey: ['master', labor.endpoint] });
    } catch (e) {
      toast.error(e);
    }
  };
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card toolbar">
        <span className="small muted">Usulkan dari absensi (HC-07)</span>
        <div style={{ width: 260 }}><LookupSelect lookup="work-orders" value={wo} onChange={setWo} filters={{ status: 'APPROVED' }} placeholder="Pilih work order" /></div>
        <input className="input" type="date" style={{ width: 'auto', height: 32 }} value={date} onChange={(e) => setDate(e.target.value)} aria-label="Tanggal" />
        <button className="btn btn-dark" type="button" disabled={wo == null} onClick={fill}>Isi dari absensi</button>
      </div>
      <MasterPage resources={[labor]} />
    </div>
  );
}
