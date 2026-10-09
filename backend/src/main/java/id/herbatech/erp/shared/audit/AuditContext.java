package id.herbatech.erp.shared.audit;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Supplier;

/**
 * Alasan perubahan untuk audit trail (ALCOA+). Diisi service sebelum menyimpan perubahan
 * yang wajib beralasan (pembatalan, reversal, koreksi master).
 * <p>
 * Di dalam transaksi, alasan bertahan sampai transaksi selesai, karena Hibernate baru menulis perubahan
 * (dan audit log-nya) saat flush menjelang commit.
 */
public final class AuditContext {

    private static final ThreadLocal<String> REASON = new ThreadLocal<>();

    private AuditContext() {
    }

    public static String reason() {
        return REASON.get();
    }

    public static void clear() {
        REASON.remove();
    }

    /** Menjalankan aksi dengan alasan tertentu. */
    public static <T> T withReason(String reason, Supplier<T> action) {
        if (reason == null || reason.isBlank()) {
            return action.get();
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            if (REASON.get() == null) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        REASON.remove();
                    }
                });
            }
            REASON.set(reason);
            return action.get();
        }
        String previous = REASON.get();
        REASON.set(reason);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                REASON.remove();
            } else {
                REASON.set(previous);
            }
        }
    }
}
