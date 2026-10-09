package id.herbatech.erp.shared.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Siklus status dokumen yang sama untuk semua aplikasi (PRD §13):
 * Draft → Diajukan → Disetujui → Diposting → Selesai, cabang Ditolak (kembali bisa diedit) dan Dibatalkan.
 */
public enum DocStatus {
    DRAFT("Draft"),
    SUBMITTED("Diajukan"),
    APPROVED("Disetujui"),
    POSTED("Diposting"),
    DONE("Selesai"),
    REJECTED("Ditolak"),
    CANCELLED("Dibatalkan");

    private static final Set<DocStatus> EDITABLE = EnumSet.of(DRAFT, REJECTED);

    private final String label;

    DocStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isEditable() {
        return EDITABLE.contains(this);
    }
}
