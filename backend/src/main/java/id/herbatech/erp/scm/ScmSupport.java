package id.herbatech.erp.scm;

import id.herbatech.erp.fin.FinApi;
import id.herbatech.erp.fin.JournalPostingService;
import id.herbatech.erp.shared.error.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bantuan bersama dokumen SCM: nama mitra/item/lokasi untuk tampilan dan jurnal persediaan per jenis item. */
@Component
class ScmSupport {

    private final JdbcTemplate jdbc;
    private final FinApi fin;
    private final JournalPostingService journals;

    ScmSupport(JdbcTemplate jdbc, FinApi fin, JournalPostingService journals) {
        this.jdbc = jdbc;
        this.fin = fin;
        this.journals = journals;
    }

    String partnerName(Long partnerId) {
        if (partnerId == null) {
            return null;
        }
        return jdbc.queryForList("SELECT name FROM sys.partner WHERE id = ?", String.class, partnerId).stream().findFirst().orElse(null);
    }

    void requirePartner(Long partnerId, String... types) {
        if (partnerId == null) {
            throw new BusinessException("PARTNER", "Mitra wajib dipilih");
        }
        String t = jdbc.queryForList("SELECT type FROM sys.partner WHERE id = ? AND active", String.class, partnerId).stream().findFirst()
                .orElseThrow(() -> new BusinessException("PARTNER", "Mitra tidak ditemukan atau nonaktif"));
        for (String ok : types) {
            if (ok.equals(t) || "BOTH".equals(t)) {
                return;
            }
        }
        throw new BusinessException("PARTNER_TYPE", "Mitra bertipe " + t + " tidak bisa dipakai di dokumen ini");
    }

    String itemLabel(Long itemId) {
        if (itemId == null) {
            return null;
        }
        return jdbc.queryForList("SELECT code || ' · ' || name FROM sys.item WHERE id = ?", String.class, itemId).stream().findFirst().orElse(null);
    }

    String locationLabel(Long locationId) {
        if (locationId == null) {
            return null;
        }
        return jdbc.queryForList("""
                SELECT w.code || ' / ' || l.bin_code FROM sys.location l JOIN sys.warehouse w ON w.id = l.warehouse_id WHERE l.id = ?""",
                String.class, locationId).stream().findFirst().orElse(null);
    }

    String lotNo(Long lotId) {
        if (lotId == null) {
            return null;
        }
        return jdbc.queryForList("SELECT lot_no FROM scm.lot WHERE id = ?", String.class, lotId).stream().findFirst().orElse(null);
    }

    boolean isQuarantine(Long locationId) {
        return Boolean.TRUE.equals(jdbc.queryForList("SELECT is_quarantine FROM sys.location WHERE id = ?", Boolean.class, locationId)
                .stream().findFirst().orElse(false));
    }

    /** Gudang barang jadi pertama di plant (asal picking surat jalan). */
    Long fgWarehouse(Long plantId) {
        return jdbc.queryForList("SELECT id FROM sys.warehouse WHERE plant_id = ? AND type = 'FG' AND active ORDER BY id LIMIT 1", Long.class, plantId)
                .stream().findFirst().orElseThrow(() -> new BusinessException("NO_FG_WH", "Belum ada gudang barang jadi di plant ini (SYS-09)"));
    }

    static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Nilai baris setelah diskon %. */
    static BigDecimal net(BigDecimal qty, BigDecimal price, BigDecimal discountPct) {
        BigDecimal gross = nz(qty).multiply(nz(price));
        return money(gross.subtract(gross.multiply(nz(discountPct)).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)));
    }

    /**
     * Jurnal persediaan: nilai per jenis item di satu sisi, akun lawan di sisi lain.
     * {@code inventoryDebit} true = persediaan bertambah (debit), false = berkurang (kredit).
     */
    void postInventory(Long plantId, LocalDate date, Map<String, BigDecimal> valueByItemType, boolean inventoryDebit,
                       Long contraAccountId, Long contraCostCenterId, String docType, Long docId, String docNo, String description) {
        Map<String, BigDecimal> values = new LinkedHashMap<>(valueByItemType);
        values.values().removeIf(v -> v == null || v.signum() == 0);
        if (values.isEmpty()) {
            return;
        }
        List<JournalPostingService.IdLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : values.entrySet()) {
            BigDecimal v = money(e.getValue());
            total = total.add(v);
            lines.add(new JournalPostingService.IdLine(fin.inventoryAccount(e.getKey()), null, description + " · " + e.getKey(),
                    inventoryDebit ? v : null, inventoryDebit ? null : v));
        }
        lines.add(new JournalPostingService.IdLine(contraAccountId, contraCostCenterId, description, inventoryDebit ? null : total,
                inventoryDebit ? total : null));
        journals.postIds(plantId, date, lines, docType, docId, docNo, description);
    }
}
