package id.herbatech.erp.scm;

import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import id.herbatech.erp.shared.web.LookupSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** SCM-40 Kartu stok & saldo per lot (baca saja) dan perubahan status lot oleh QA. */
@RestController
@RequestMapping("/api/scm")
class StockController {

    record QuantRow(Long itemId, String itemCode, String itemName, Long lotId, String lotNo, String qcStatus,
                    LocalDate expDate, Long locationId, String warehouse, String binCode, BigDecimal qty,
                    BigDecimal qtyReserved, BigDecimal unitCost) {
    }

    record MoveRow(Long id, String moveType, String itemCode, String lotNo, String fromBin, String toBin, BigDecimal qty,
                   BigDecimal unitCost, LocalDate moveDate, String refDocType, Long refDocId, String refDocNo,
                   String reason, Instant createdAt) {
    }

    record StatusRequest(Lot.QcStatus status, String reason, String password) {
    }

    private final JdbcTemplate jdbc;
    private final PermissionService perm;
    private final LotStatusService lotStatus;

    StockController(JdbcTemplate jdbc, PermissionService perm, LotStatusService lotStatus) {
        this.jdbc = jdbc;
        this.perm = perm;
        this.lotStatus = lotStatus;
    }

    @GetMapping("/stock")
    public List<QuantRow> stock(@RequestParam(required = false) Long itemId,
                                @RequestParam(required = false) Long warehouseId,
                                @RequestParam(required = false) String qcStatus) {
        perm.require("SCM-40", Action.VIEW);
        StringBuilder sql = new StringBuilder("""
                SELECT q.item_id, i.code, i.name, q.lot_id, l.lot_no, l.qc_status, l.exp_date, q.location_id,
                       w.code AS wh, loc.bin_code, q.qty, q.qty_reserved, q.unit_cost
                FROM scm.stock_quant q
                JOIN sys.item i ON i.id = q.item_id
                JOIN sys.location loc ON loc.id = q.location_id
                JOIN sys.warehouse w ON w.id = loc.warehouse_id
                LEFT JOIN scm.lot l ON l.id = q.lot_id
                WHERE q.qty > 0 AND w.plant_id = ?""");
        List<Object> args = new ArrayList<>(List.of(UserContext.plantId()));
        if (itemId != null) {
            sql.append(" AND q.item_id = ?");
            args.add(itemId);
        }
        if (warehouseId != null) {
            sql.append(" AND w.id = ?");
            args.add(warehouseId);
        }
        if (qcStatus != null && !qcStatus.isBlank()) {
            sql.append(" AND l.qc_status = ?");
            args.add(qcStatus);
        }
        sql.append(" ORDER BY i.code, l.exp_date NULLS LAST, l.lot_no, loc.bin_code LIMIT 5000");
        return jdbc.query(sql.toString(), (rs, i) -> new QuantRow(rs.getLong("item_id"), rs.getString("code"),
                rs.getString("name"), (Long) rs.getObject("lot_id"), rs.getString("lot_no"), rs.getString("qc_status"),
                rs.getDate("exp_date") == null ? null : rs.getDate("exp_date").toLocalDate(), rs.getLong("location_id"),
                rs.getString("wh"), rs.getString("bin_code"), rs.getBigDecimal("qty"), rs.getBigDecimal("qty_reserved"),
                rs.getBigDecimal("unit_cost")), args.toArray());
    }

    @GetMapping("/moves")
    public List<MoveRow> moves(@RequestParam(required = false) Long itemId, @RequestParam(required = false) Long lotId) {
        perm.require("SCM-40", Action.VIEW);
        StringBuilder sql = new StringBuilder("""
                SELECT m.*, i.code AS item_code, l.lot_no, f.bin_code AS from_bin, t.bin_code AS to_bin
                FROM scm.stock_move m
                JOIN sys.item i ON i.id = m.item_id
                LEFT JOIN scm.lot l ON l.id = m.lot_id
                LEFT JOIN sys.location f ON f.id = m.from_loc_id
                LEFT JOIN sys.location t ON t.id = m.to_loc_id
                WHERE 1=1""");
        List<Object> args = new ArrayList<>();
        if (itemId != null) {
            sql.append(" AND m.item_id = ?");
            args.add(itemId);
        }
        if (lotId != null) {
            sql.append(" AND m.lot_id = ?");
            args.add(lotId);
        }
        sql.append(" ORDER BY m.created_at DESC, m.id DESC LIMIT 1000");
        return jdbc.query(sql.toString(), (rs, i) -> new MoveRow(rs.getLong("id"), rs.getString("move_type"),
                rs.getString("item_code"), rs.getString("lot_no"), rs.getString("from_bin"), rs.getString("to_bin"),
                rs.getBigDecimal("qty"), rs.getBigDecimal("unit_cost"), rs.getDate("move_date").toLocalDate(),
                rs.getString("ref_doc_type"), rs.getLong("ref_doc_id"), rs.getString("ref_doc_no"), rs.getString("reason"),
                rs.getTimestamp("created_at").toInstant()), args.toArray());
    }

    @PostMapping("/lots/{lotId}/status")
    public Map<String, Object> changeStatus(@PathVariable Long lotId, @RequestBody StatusRequest req) {
        Lot lot = lotStatus.change(lotId, req.status(), req.reason(), req.password());
        return Map.of("id", lot.getId(), "lotNo", lot.getLotNo(), "qcStatus", lot.getQcStatus().name());
    }

    @Component
    static class ScmLookups implements LookupSource {
        @Override
        public Map<String, Def> lookups() {
            return Map.of("lots", new Def("scm.lot", "lot_no", "supplier_lot", "qc_status", null, Set.of("item_id", "qc_status")));
        }
    }
}
