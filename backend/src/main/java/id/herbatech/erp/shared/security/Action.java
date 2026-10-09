package id.herbatech.erp.shared.security;

/** Aksi yang dikontrol per menu (PRD §2): lihat, buat, ubah, submit, approve, posting, batal, ekspor, dll. */
public enum Action {
    VIEW, CREATE, EDIT, SUBMIT, APPROVE, POST, CANCEL, EXPORT,
    /** Mengubah status lot (Quarantine → Released/Rejected). Hanya QA Release Officer. */
    RELEASE,
    /** Membaca audit trail global. */
    AUDIT,
    /** Mengelola master & konfigurasi SYS. */
    ADMIN,
    /** Melihat data gaji. */
    PAYROLL
}
