package id.herbatech.erp.shared.meta;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

/**
 * Data pembentuk SARMUT (PRD §16): setiap modul menghitung indikatornya sendiri dari transaksi dan
 * menyerahkannya ke HC-13. Kunci indikator dicocokkan dengan {@code sarmut_kpi.auto_key}.
 * Indikator yang belum punya data periode itu tidak dikembalikan (bukan nol).
 */
public interface KpiProvider {

    Map<String, BigDecimal> compute(YearMonth period, Long plantId);
}
