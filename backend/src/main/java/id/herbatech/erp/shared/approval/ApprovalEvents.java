package id.herbatech.erp.shared.approval;

/** Peristiwa approval. Completed/Rejected diproses sinkron dalam transaksi yang sama oleh workflow dokumen. */
public final class ApprovalEvents {

    private ApprovalEvents() {
    }

    /** Semua level sudah disetujui. */
    public record ApprovalCompleted(String docType, Long docId, Long lastApproverId) {
    }

    /** Salah satu level menolak. */
    public record ApprovalRejected(String docType, Long docId, Long rejectedBy, String reason) {
    }

    /** Sebuah level menjadi aktif dan perlu keputusan (dipakai untuk notifikasi, asinkron). */
    public record ApprovalTaskActivated(Long taskId) {
    }
}
