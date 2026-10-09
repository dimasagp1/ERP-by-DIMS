package id.herbatech.erp.shared.meta;

import java.util.List;

/**
 * Angka utama dashboard aplikasi (PRD §15.3: angka dengan pembanding). Setiap modul menyediakan KPI
 * yang dihitung dari transaksinya sendiri; tidak ada angka contoh.
 */
public interface DashboardProvider {

    record Kpi(String label, String value, String note, String menuCode) {
    }

    String appCode();

    List<Kpi> kpis(Long plantId);
}
