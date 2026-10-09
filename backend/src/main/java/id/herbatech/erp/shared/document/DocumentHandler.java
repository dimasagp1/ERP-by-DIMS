package id.herbatech.erp.shared.document;

import id.herbatech.erp.shared.domain.DocumentEntity;
import id.herbatech.erp.shared.error.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Kontrak satu jenis dokumen terhadap {@link DocumentWorkflowService}. Modul hanya menulis aturan khas
 * dokumennya (validasi, efek posting, reversal); siklus status, approval, nomor, audit, aktivitas,
 * notifikasi, dan kunci periode ditangani kernel.
 */
public interface DocumentHandler<D extends DocumentEntity> {

    /** Kode jenis dokumen di SYS-05, mis. {@code JV}. */
    String docType();

    /** Modul untuk pengecekan kunci periode, mis. {@code FIN}, {@code SCM}. */
    String periodModule();

    D load(Long id);

    D save(D doc);

    /** Ringkasan satu baris untuk antrean kerja, kotak approval, dan pencarian. */
    String summary(D doc);

    /** Nilai dokumen untuk matriks approval (null = 0). */
    default BigDecimal amount(D doc) {
        return null;
    }

    /**
     * Veto aksi khusus dokumen (mis. jurnal otomatis tidak bisa diedit). Aksi: EDIT, SUBMIT, POST, CANCEL, REVERSE.
     */
    default boolean allows(String action, D doc) {
        return true;
    }

    /** Pengguna yang mengajukan (dasar atasan langsung & pemisahan tugas). Bawaan: pembuat dokumen. */
    default Long requesterId(D doc) {
        return doc.getCreatedBy();
    }

    /** Penanda kondisi untuk level approval bersyarat (lihat {@code ApprovalRule.conditionKey}). */
    default java.util.Set<String> approvalFlags(D doc) {
        return java.util.Set.of();
    }

    /** Validasi kelengkapan sebelum diajukan. */
    default void validateSubmit(D doc) {
    }

    /** Dipanggil saat semua level approval selesai. */
    default void onApproved(D doc) {
    }

    /**
     * Dokumen operasional (mis. penerimaan barang, transfer, surat jalan) langsung diposting begitu disetujui,
     * tanpa langkah posting terpisah oleh Manager.
     */
    default boolean autoPost(D doc) {
        return false;
    }

    /** Efek posting: gerak stok, jurnal otomatis, tugas di departemen lain. */
    default void onPost(D doc) {
    }

    /** Dipanggil saat dokumen yang belum diposting dibatalkan. */
    default void onCancel(D doc) {
    }

    /** Membuat dokumen balik (reversal) untuk dokumen terposting. Dokumen balik dikembalikan dalam status POSTED. */
    default D reverse(D doc, LocalDate date, String reason) {
        throw new BusinessException("NO_REVERSAL", "Dokumen " + docType() + " tidak mendukung reversal");
    }
}
