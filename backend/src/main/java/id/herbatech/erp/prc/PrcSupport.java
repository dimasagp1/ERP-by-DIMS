package id.herbatech.erp.prc;

import id.herbatech.erp.shared.error.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;

/** Bantuan bersama dokumen Procurement: nama mitra/item, kualifikasi supplier, ASL, harga kontrak, kurs. */
@Component
class PrcSupport {

    private final JdbcTemplate jdbc;

    PrcSupport(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    String partnerName(Long partnerId) {
        if (partnerId == null) {
            return null;
        }
        return jdbc.queryForList("SELECT name FROM sys.partner WHERE id = ?", String.class, partnerId).stream().findFirst().orElse(null);
    }

    Map<String, Object> item(Long itemId) {
        if (itemId == null) {
            throw new BusinessException("ITEM", "Item wajib dipilih");
        }
        return jdbc.queryForList("""
                SELECT i.id, i.code, i.name, i.type, i.status, i.halal_critical, u.code AS uom FROM sys.item i JOIN sys.uom u ON u.id = i.uom_id
                WHERE i.id = ?""", itemId).stream().findFirst().orElseThrow(() -> new BusinessException("ITEM", "Item tidak ditemukan"));
    }

    String itemLabel(Long itemId) {
        if (itemId == null) {
            return null;
        }
        return jdbc.queryForList("SELECT code || ' · ' || name FROM sys.item WHERE id = ?", String.class, itemId).stream().findFirst().orElse(null);
    }

    /** Supplier aktif yang lolos kualifikasi (PRC-03): QUALIFIED atau CONDITIONAL. */
    void requireQualifiedSupplier(Long partnerId) {
        if (partnerId == null) {
            throw new BusinessException("PO_PARTNER", "Supplier wajib dipilih");
        }
        String type = jdbc.queryForList("SELECT type FROM sys.partner WHERE id = ? AND active", String.class, partnerId).stream().findFirst()
                .orElseThrow(() -> new BusinessException("PO_PARTNER", "Supplier tidak ditemukan atau nonaktif"));
        if (!"SUPPLIER".equals(type) && !"BOTH".equals(type) && !"EXPEDITION".equals(type)) {
            throw new BusinessException("PO_PARTNER", "Mitra bukan supplier");
        }
        String status = jdbc.queryForList("SELECT qualification_status FROM prc.supplier_profile WHERE partner_id = ? AND active", String.class,
                partnerId).stream().findFirst().orElse(null);
        if (status == null) {
            throw new BusinessException("SUPPLIER_PROFILE", partnerName(partnerId) + " belum diregistrasi sebagai supplier (PRC-03)");
        }
        if (!"QUALIFIED".equals(status) && !"CONDITIONAL".equals(status)) {
            throw new BusinessException("SUPPLIER_QUAL", partnerName(partnerId) + " berstatus " + status + "; belum boleh menerima PO");
        }
    }

    /** Bahan baku & kemas hanya boleh dibeli dari kombinasi item × supplier di ASL (PRC-04) yang berlaku. */
    void requireAsl(Long itemId, Long partnerId, LocalDate on, String where) {
        Map<String, Object> i = item(itemId);
        if (!"RM".equals(i.get("type")) && !"PM".equals(i.get("type"))) {
            return;
        }
        Long ok = jdbc.queryForObject("""
                SELECT count(*) FROM prc.asl WHERE item_id = ? AND partner_id = ? AND active AND status IN ('APPROVED','CONDITIONAL')
                  AND (valid_until IS NULL OR valid_until >= ?)""", Long.class, itemId, partnerId, Date.valueOf(on));
        if (ok == null || ok == 0) {
            throw new BusinessException("ASL", where + ": " + i.get("code") + " dari " + partnerName(partnerId)
                    + " tidak ada di Daftar Supplier Disetujui (PRC-04)");
        }
    }

    /** Harga kontrak yang berlaku (PRC-08) untuk supplier × item pada tanggal & qty tertentu. */
    BigDecimal contractPrice(Long partnerId, Long itemId, LocalDate on, BigDecimal qty) {
        return jdbc.queryForList("""
                SELECT price FROM prc.price_list WHERE partner_id = ? AND item_id = ? AND active AND valid_from <= ?
                  AND (valid_until IS NULL OR valid_until >= ?) AND min_qty <= ? ORDER BY min_qty DESC, valid_from DESC LIMIT 1""",
                BigDecimal.class, partnerId, itemId, Date.valueOf(on), Date.valueOf(on), qty == null ? BigDecimal.ZERO : qty)
                .stream().findFirst().orElse(null);
    }

    /** Harga termurah antar-supplier (perkiraan harga PR). */
    BigDecimal bestPrice(Long itemId, LocalDate on) {
        return jdbc.queryForList("""
                SELECT price FROM prc.price_list WHERE item_id = ? AND active AND valid_from <= ? AND (valid_until IS NULL OR valid_until >= ?)
                ORDER BY price LIMIT 1""", BigDecimal.class, itemId, Date.valueOf(on), Date.valueOf(on)).stream().findFirst()
                .orElseGet(() -> jdbc.queryForList("SELECT unit_price FROM prc.po_line l JOIN prc.po p ON p.id = l.po_id WHERE l.item_id = ? "
                        + "AND p.status IN ('APPROVED','DONE') ORDER BY p.doc_date DESC LIMIT 1", BigDecimal.class, itemId).stream().findFirst()
                        .orElse(BigDecimal.ZERO));
    }

    /** Kurs tengah terakhir s.d. tanggal (SYS-11); IDR = 1. */
    BigDecimal rate(String currency, LocalDate on) {
        if (currency == null || "IDR".equals(currency)) {
            return BigDecimal.ONE;
        }
        return jdbc.queryForList("""
                SELECT rate FROM sys.exchange_rate WHERE currency_code = ? AND rate_date <= ? ORDER BY rate_date DESC LIMIT 1""",
                BigDecimal.class, currency, Date.valueOf(on)).stream().findFirst()
                .orElseThrow(() -> new BusinessException("RATE", "Kurs " + currency + " belum diisi di SYS-11"));
    }

    static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    static BigDecimal net(BigDecimal qty, BigDecimal price, BigDecimal discountPct) {
        BigDecimal gross = nz(qty).multiply(nz(price));
        return money(gross.subtract(gross.multiply(nz(discountPct)).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)));
    }
}
