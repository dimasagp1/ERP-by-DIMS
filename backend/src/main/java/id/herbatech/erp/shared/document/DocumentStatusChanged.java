package id.herbatech.erp.shared.document;

/**
 * Peristiwa perubahan status dokumen. Dipublikasikan dalam transaksi dan disimpan di event publication
 * registry (outbox) sehingga listener modul lain tetap menerima walau aplikasi restart.
 */
public record DocumentStatusChanged(String docType, Long docId, String docNo, String appCode, Long plantId,
                                    String fromStatus, String toStatus, Long actorId, Long createdBy, String reason) {
}
