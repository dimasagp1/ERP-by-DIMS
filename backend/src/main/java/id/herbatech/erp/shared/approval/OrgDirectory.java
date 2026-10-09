package id.herbatech.erp.shared.approval;

import id.herbatech.erp.shared.security.ViewScope;

import java.time.LocalDate;
import java.util.Set;

/**
 * Struktur organisasi yang dibutuhkan kernel (diimplementasikan modul HC dari HC-02/HC-03):
 * siapa atasan langsung seseorang, dan dokumen siapa yang boleh dilihat menurut cakupan peran.
 */
public interface OrgDirectory {

    /**
     * Pengguna atasan langsung dari pembuat dokumen. Posisi kosong dilewati ke atas; bila atasan sedang
     * didelegasikan (cuti) sampai tanggal tertentu, pengganti yang dikembalikan.
     *
     * @return id pengguna, atau null bila tidak ditemukan
     */
    Long superiorUserId(Long userId, LocalDate onDate);

    /** Id pengguna pembuat yang dokumennya boleh dilihat; null berarti tidak dibatasi. */
    Set<Long> visibleCreatorIds(Long userId, ViewScope scope);
}
