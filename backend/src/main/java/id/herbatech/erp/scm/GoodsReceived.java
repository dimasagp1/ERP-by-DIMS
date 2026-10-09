package id.herbatech.erp.scm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Peristiwa penerimaan barang terposting (atau dibalik bila qty negatif). Procurement memperbarui qty diterima di PO,
 * menutup PO yang terpenuhi, dan memakai komitmen anggaran. Dipublikasikan di dalam transaksi posting.
 */
public record GoodsReceived(Long grId, String grNo, Long poId, LocalDate date, List<Line> lines) {

    public record Line(Long poLineId, BigDecimal qty, BigDecimal amount) {
    }
}
