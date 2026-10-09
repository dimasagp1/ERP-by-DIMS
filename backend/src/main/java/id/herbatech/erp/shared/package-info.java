/**
 * Shared kernel: aturan lintas modul PRD §13 yang berlaku di semua aplikasi
 * (siklus dokumen, penomoran, approval, audit trail, tanda tangan elektronik,
 * kunci periode, hak akses, notifikasi, lampiran). Modul departemen bergantung
 * pada paket ini; paket ini tidak boleh bergantung pada modul departemen.
 */
@ApplicationModule(displayName = "Shared Kernel", type = ApplicationModule.Type.OPEN)
package id.herbatech.erp.shared;

import org.springframework.modulith.ApplicationModule;
