package id.herbatech.erp.scm;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/** BOM berlaku per produk untuk modul lain (PRE permintaan bahan, MRP). */
@Service
public class BomQueries {

    public record Component(Long itemId, BigDecimal qty, BigDecimal scrapPct) {
    }

    public record ActiveBom(Long id, String docNo, int revision, Long itemId, BigDecimal baseQty, BigDecimal stdHours,
                            List<Component> components) {
    }

    /** Kebutuhan komponen untuk sejumlah produk, termasuk susut (scrap %). */
    public record Requirement(Long itemId, String code, String name, String uom, String type, BigDecimal qty) {
    }

    private final JdbcTemplate jdbc;
    private final InventoryService inventory;

    BomQueries(JdbcTemplate jdbc, InventoryService inventory) {
        this.jdbc = jdbc;
        this.inventory = inventory;
    }

    InventoryService inventory() {
        return inventory;
    }

    public Optional<ActiveBom> active(Long plantId, Long itemId) {
        return jdbc.query("""
                SELECT id, doc_no, revision, base_qty, std_hours FROM scm.bom
                WHERE item_id = ? AND plant_id = ? AND status = 'POSTED' ORDER BY revision DESC LIMIT 1""", rs -> {
            if (!rs.next()) {
                return Optional.<ActiveBom>empty();
            }
            Long id = rs.getLong(1);
            List<Component> comps = jdbc.query("SELECT component_item_id, qty, scrap_pct FROM scm.bom_line WHERE bom_id = ? ORDER BY line_no",
                    (r, i) -> new Component(r.getLong(1), r.getBigDecimal(2), r.getBigDecimal(3)), id);
            return Optional.of(new ActiveBom(id, rs.getString(2), rs.getInt(3), itemId, rs.getBigDecimal(4), rs.getBigDecimal(5), comps));
        }, itemId, plantId);
    }

    /** Ledakan satu level: qty komponen = qty produk / jumlah dasar × qty BOM × (1 + susut%). */
    public List<Requirement> explode(Long plantId, Long itemId, BigDecimal qty) {
        return active(plantId, itemId).map(b -> b.components().stream().map(c -> {
            InventoryService.ItemInfo i = inventory.item(c.itemId());
            BigDecimal need = qty.multiply(c.qty()).divide(b.baseQty(), 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.ONE.add(c.scrapPct().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)))
                    .setScale(4, RoundingMode.UP);
            return new Requirement(c.itemId(), i.code(), i.name(), i.uom(), i.type(), need);
        }).toList()).orElse(List.of());
    }
}
