package id.herbatech.erp.fin;

import id.herbatech.erp.shared.error.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Parameter FIN-99, perhitungan PPN, dan validasi akun/cost center untuk baris dokumen keuangan. */
@Component
class FinSupport {

    private final JdbcTemplate jdbc;

    FinSupport(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    String param(String key, String def) {
        return jdbc.queryForList("SELECT value FROM fin.fin_param WHERE key = ? AND active", String.class, key)
                .stream().findFirst().orElse(def);
    }

    BigDecimal paramNum(String key, String def) {
        return new BigDecimal(param(key, def));
    }

    /** PPN = tarif × DPP nilai lain (11/12 × harga, PMK 131/2024). */
    BigDecimal ppn(BigDecimal base) {
        BigDecimal dpp = base.multiply(paramNum("PPN_DPP_NUM", "11")).divide(paramNum("PPN_DPP_DEN", "12"), 10, RoundingMode.HALF_UP);
        return dpp.multiply(paramNum("PPN_RATE", "0.12")).setScale(0, RoundingMode.HALF_UP);
    }

    /** Tarif PPh dari kode pajak SYS-12 (persen → pecahan). */
    BigDecimal taxRate(String taxCode) {
        BigDecimal pct = jdbc.queryForList("SELECT rate FROM sys.tax_code WHERE code = ? AND active", BigDecimal.class, taxCode)
                .stream().findFirst().orElseThrow(() -> new BusinessException("TAX", "Kode pajak " + taxCode + " tidak ditemukan"));
        return pct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    }

    /** Akun harus akun detail aktif; cost center wajib bila akun memintanya. */
    void validateAccount(Long accountId, Long costCenterId, String where) {
        if (accountId == null) {
            throw new BusinessException("ACCOUNT", where + ": akun wajib dipilih");
        }
        Map<String, Object> a = jdbc.queryForList("SELECT code, postable, active, requires_cost_center FROM fin.account WHERE id = ?", accountId)
                .stream().findFirst().orElseThrow(() -> new BusinessException("ACCOUNT", where + ": akun tidak ditemukan"));
        if (!(Boolean) a.get("postable") || !(Boolean) a.get("active")) {
            throw new BusinessException("ACCOUNT", where + ": akun " + a.get("code") + " adalah akun header atau tidak aktif");
        }
        if ((Boolean) a.get("requires_cost_center") && costCenterId == null) {
            throw new BusinessException("ACCOUNT_CC", where + ": akun " + a.get("code") + " wajib cost center");
        }
    }

    Long glAccountOfBank(Long bankAccountId) {
        return jdbc.queryForList("SELECT gl_account_id FROM fin.bank_account WHERE id = ? AND active", Long.class, bankAccountId)
                .stream().findFirst().orElseThrow(() -> new BusinessException("BANK", "Rekening bank/kas tidak ditemukan atau tidak aktif"));
    }

    String partnerName(Long partnerId) {
        return partnerId == null ? null : jdbc.queryForList("SELECT name FROM sys.partner WHERE id = ?", String.class, partnerId)
                .stream().findFirst().orElse(null);
    }

    void requirePartner(Long partnerId, String... types) {
        String t = jdbc.queryForList("SELECT type FROM sys.partner WHERE id = ? AND active", String.class, partnerId)
                .stream().findFirst().orElseThrow(() -> new BusinessException("PARTNER", "Mitra bisnis tidak ditemukan atau tidak aktif"));
        for (String ok : types) {
            if (ok.equals(t) || "BOTH".equals(t)) {
                return;
            }
        }
        throw new BusinessException("PARTNER", "Mitra bisnis bertipe " + t + " tidak bisa dipakai di dokumen ini");
    }
}
