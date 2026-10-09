package id.herbatech.erp.shared.domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Entitas yang tidak dicatat di audit trail per field, karena sudah menjadi catatan tak berubah sendiri
 * (mis. notifikasi, log aktivitas, saldo stok yang jejaknya ada di stock_move).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SkipAudit {
}
