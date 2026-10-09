package id.herbatech.erp.scm;

import id.herbatech.erp.fin.FinApi;
import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.error.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * SCM-05 MRP: kebutuhan kotor dari MPS diledakkan lewat BOM berlaku (multi-level), dikurangi stok (Released + karantina),
 * PO berjalan, dan PR terbuka, ditambah stok pengaman; hasilnya usulan beli (PR) atau buat (WO) bertanggal kebutuhan
 * dan tanggal pesan (kebutuhan − lead time). Dijalankan manual atau setiap malam (PRD: ≤ 10 menit).
 */
@Slf4j
@Service
public class MrpService {

    /** Usulan MRP yang belum menjadi PR (dipakai Procurement saat membuat PR dari MRP). */
    public record Suggestion(Long lineId, Long runId, Long itemId, BigDecimal plannedQty, LocalDate needDate, Long partnerId,
                             BigDecimal estPrice) {
    }

    private record Demand(Map<Long, TreeMap<LocalDate, BigDecimal>> gross, Map<Long, Integer> level, Map<Long, List<String>> pegging) {
    }

    private final JdbcTemplate jdbc;
    private final BomQueries boms;
    private final FinApi fin;
    private final TimeService time;

    MrpService(JdbcTemplate jdbc, BomQueries boms, FinApi fin, TimeService time) {
        this.jdbc = jdbc;
        this.boms = boms;
        this.fin = fin;
        this.time = time;
    }

    static LocalDate monday(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** MRP malam hari untuk setiap plant yang punya MPS. */
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Jakarta")
    public void nightly() {
        if (!"true".equalsIgnoreCase(fin.param("MRP_NIGHTLY", "true"))) {
            return;
        }
        for (Long plant : jdbc.queryForList("SELECT DISTINCT plant_id FROM scm.mps WHERE qty > 0", Long.class)) {
            try {
                run(plant, "NIGHTLY", null);
            } catch (RuntimeException e) {
                log.warn("MRP malam plant {} gagal: {}", plant, e.getMessage());
            }
        }
    }

    @Transactional
    public Long run(Long plantId, String trigger, Long userId) {
        long started = System.currentTimeMillis();
        int horizon = fin.paramNum("MRP_HORIZON_WEEKS", "12").intValue();
        LocalDate from = monday(time.today());
        LocalDate to = from.plusWeeks(horizon);
        Long runId = jdbc.queryForObject("""
                INSERT INTO scm.mrp_run (plant_id, horizon_weeks, trigger, triggered_by) VALUES (?, ?, ?, ?) RETURNING id""",
                Long.class, plantId, horizon, trigger, userId);

        Demand demand = explode(plantId, from, to);
        int count = 0;
        List<Long> items = new ArrayList<>(demand.gross().keySet());
        // Item di bawah titik pesan ulang tanpa kebutuhan terjadwal (mis. sparepart) tetap diusulkan.
        items.addAll(jdbc.queryForList("""
                SELECT sp.item_id FROM scm.stock_param sp JOIN sys.item i ON i.id = sp.item_id
                WHERE sp.plant_id = ? AND sp.active AND sp.reorder_point > 0 AND i.type NOT IN ('FG','WIP')""", Long.class, plantId)
                .stream().filter(i -> !demand.gross().containsKey(i)).toList());

        for (Long itemId : items) {
            InventoryService.ItemInfo item = boms.inventory().item(itemId);
            TreeMap<LocalDate, BigDecimal> weeks = demand.gross().getOrDefault(itemId, new TreeMap<>());
            BigDecimal gross = weeks.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean make = "FG".equals(item.type()) || ("WIP".equals(item.type()) && boms.active(plantId, itemId).isPresent());
            Map<String, Object> p = param(plantId, itemId);
            BigDecimal ss = (BigDecimal) p.get("safety_stock");
            BigDecimal[] stock = stock(plantId, itemId);
            BigDecimal onOrder = make ? openWo(plantId, itemId) : openPo(plantId, itemId).add(openPr(plantId, itemId));
            BigDecimal net;
            LocalDate need;
            if (make && demand.level().getOrDefault(itemId, 0) == 0) {
                // MPS sudah merupakan rencana produksi bersih dari perencana; MRP mencatatnya sebagai usulan buat.
                net = gross;
                need = weeks.isEmpty() ? from : weeks.firstKey();
            } else {
                BigDecimal available = stock[0].add(stock[1]).add(onOrder).subtract(ss);
                net = gross.subtract(available);
                need = firstShortage(weeks, available, from);
                BigDecimal rop = (BigDecimal) p.get("reorder_point");
                if (weeks.isEmpty() && rop.signum() > 0 && stock[0].add(stock[1]).add(onOrder).compareTo(rop) <= 0) {
                    BigDecimal max = p.get("max_qty") == null ? rop.multiply(BigDecimal.valueOf(2)) : (BigDecimal) p.get("max_qty");
                    net = max.subtract(stock[0].add(stock[1]).add(onOrder));
                    need = from;
                }
            }
            if (net.signum() <= 0) {
                continue;
            }
            BigDecimal planned = make && demand.level().getOrDefault(itemId, 0) == 0 ? net : roundUp(net, (BigDecimal) p.get("moq"),
                    (BigDecimal) p.get("lot_size"));
            Map<String, Object> source = make ? Map.of() : bestPrice(itemId, need);
            int lead = make ? 0 : source.get("lead_time_days") != null ? ((Number) source.get("lead_time_days")).intValue()
                    : ((Number) p.get("lead_time_days")).intValue();
            jdbc.update("""
                    INSERT INTO scm.mrp_line (run_id, item_id, level, action, gross_req, on_hand, quarantine, on_order, safety_stock,
                                              net_req, planned_qty, need_date, order_date, partner_id, est_price, pegging)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    runId, itemId, demand.level().getOrDefault(itemId, 0), make ? "MAKE" : "BUY", gross, stock[0], stock[1], onOrder, ss,
                    net, planned, Date.valueOf(need), Date.valueOf(need.minusDays(lead)), source.get("partner_id"), source.get("price"),
                    String.join("; ", demand.pegging().getOrDefault(itemId, List.of())));
            count++;
        }
        long ms = System.currentTimeMillis() - started;
        jdbc.update("UPDATE scm.mrp_run SET duration_ms = ?, lines = ? WHERE id = ?", ms, count, runId);
        jdbc.update("DELETE FROM scm.mrp_run WHERE plant_id = ? AND id NOT IN (SELECT id FROM scm.mrp_run WHERE plant_id = ? ORDER BY id DESC LIMIT 10)",
                plantId, plantId);
        log.info("MRP plant {} selesai: {} baris dalam {} ms", plantId, count, ms);
        return runId;
    }

    /** Ledakan MPS melalui BOM berlaku sampai bahan beli; kebutuhan per minggu & asal kebutuhan (pegging). */
    private Demand explode(Long plantId, LocalDate from, LocalDate to) {
        Map<Long, TreeMap<LocalDate, BigDecimal>> gross = new LinkedHashMap<>();
        Map<Long, Integer> level = new HashMap<>();
        Map<Long, List<String>> pegging = new HashMap<>();
        List<Object[]> queue = new ArrayList<>();
        jdbc.query("SELECT item_id, week_start, qty FROM scm.mps WHERE plant_id = ? AND week_start >= ? AND week_start < ? AND qty > 0 ORDER BY week_start",
                rs -> {
                    queue.add(new Object[]{rs.getLong(1), rs.getDate(2).toLocalDate(), rs.getBigDecimal(3), 0, "MPS"});
                }, plantId, Date.valueOf(from), Date.valueOf(to));
        int guard = 0;
        while (!queue.isEmpty()) {
            if (++guard > 100_000) {
                throw new BusinessException("MRP_LOOP", "Struktur BOM terlalu dalam atau melingkar");
            }
            Object[] q = queue.removeFirst();
            Long item = (Long) q[0];
            LocalDate week = (LocalDate) q[1];
            BigDecimal qty = (BigDecimal) q[2];
            int lvl = (Integer) q[3];
            gross.computeIfAbsent(item, k -> new TreeMap<>()).merge(week, qty, BigDecimal::add);
            level.merge(item, lvl, Math::max);
            String peg = (String) q[4];
            List<String> pegs = pegging.computeIfAbsent(item, k -> new ArrayList<>());
            if (pegs.size() < 5 && !pegs.contains(peg)) {
                pegs.add(peg);
            }
            if (lvl > 0 && !"WIP".equals(boms.inventory().item(item).type())) {
                continue;
            }
            String code = boms.inventory().item(item).code();
            for (BomQueries.Requirement r : boms.explode(plantId, item, qty)) {
                queue.add(new Object[]{r.itemId(), week, r.qty(), lvl + 1, code});
            }
        }
        return new Demand(gross, level, pegging);
    }

    private static LocalDate firstShortage(TreeMap<LocalDate, BigDecimal> weeks, BigDecimal available, LocalDate from) {
        BigDecimal cum = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> e : weeks.entrySet()) {
            cum = cum.add(e.getValue());
            if (cum.compareTo(available) > 0) {
                return e.getKey();
            }
        }
        return weeks.isEmpty() ? from : weeks.lastKey();
    }

    static BigDecimal roundUp(BigDecimal net, BigDecimal moq, BigDecimal lotSize) {
        BigDecimal q = net.max(moq == null ? BigDecimal.ZERO : moq);
        if (lotSize != null && lotSize.signum() > 0) {
            q = q.divide(lotSize, 0, RoundingMode.CEILING).multiply(lotSize);
        }
        return q.setScale(4, RoundingMode.HALF_UP);
    }

    private Map<String, Object> param(Long plantId, Long itemId) {
        return jdbc.queryForList("""
                SELECT safety_stock, reorder_point, max_qty, lead_time_days, moq, lot_size FROM scm.stock_param
                WHERE plant_id = ? AND item_id = ? AND active""", plantId, itemId).stream().findFirst()
                .orElseGet(() -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("safety_stock", BigDecimal.ZERO);
                    m.put("reorder_point", BigDecimal.ZERO);
                    m.put("max_qty", null);
                    m.put("lead_time_days", 14);
                    m.put("moq", BigDecimal.ZERO);
                    m.put("lot_size", BigDecimal.ZERO);
                    return m;
                });
    }

    /** [Released siap pakai, di karantina menunggu QC] untuk plant. */
    private BigDecimal[] stock(Long plantId, Long itemId) {
        return jdbc.query("""
                SELECT COALESCE(SUM(q.qty - q.qty_reserved) FILTER (WHERE NOT loc.is_quarantine AND (l.id IS NULL OR (l.qc_status = 'RELEASED'
                            AND (l.exp_date IS NULL OR l.exp_date >= current_date)))), 0),
                       COALESCE(SUM(q.qty) FILTER (WHERE loc.is_quarantine AND (l.id IS NULL OR l.qc_status IN ('QUARANTINE','RELEASED'))), 0)
                FROM scm.stock_quant q JOIN sys.location loc ON loc.id = q.location_id JOIN sys.warehouse w ON w.id = loc.warehouse_id
                LEFT JOIN scm.lot l ON l.id = q.lot_id WHERE q.item_id = ? AND w.plant_id = ?""",
                rs -> {
                    rs.next();
                    return new BigDecimal[]{rs.getBigDecimal(1), rs.getBigDecimal(2)};
                }, itemId, plantId);
    }

    private BigDecimal openPo(Long plantId, Long itemId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(GREATEST(l.qty - l.received_qty, 0)), 0) FROM prc.po_line l JOIN prc.po p ON p.id = l.po_id
                WHERE l.item_id = ? AND p.plant_id = ? AND p.status IN ('SUBMITTED','APPROVED') AND NOT l.closed""", BigDecimal.class, itemId, plantId);
    }

    private BigDecimal openPr(Long plantId, Long itemId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(GREATEST(l.qty - l.qty_ordered, 0)), 0) FROM prc.pr_line l JOIN prc.pr r ON r.id = l.pr_id
                WHERE l.item_id = ? AND r.plant_id = ? AND r.status IN ('DRAFT','SUBMITTED','APPROVED') AND NOT l.closed""",
                BigDecimal.class, itemId, plantId);
    }

    private BigDecimal openWo(Long plantId, Long itemId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(qty_plan), 0) FROM pre.work_order WHERE product_item_id = ? AND plant_id = ?
                  AND status IN ('DRAFT','SUBMITTED','APPROVED')""", BigDecimal.class, itemId, plantId);
    }

    /** Harga kontrak termurah yang berlaku pada tanggal kebutuhan (PRC-08). */
    Map<String, Object> bestPrice(Long itemId, LocalDate on) {
        return jdbc.queryForList("""
                SELECT partner_id, price, lead_time_days FROM prc.price_list
                WHERE item_id = ? AND active AND valid_from <= ? AND (valid_until IS NULL OR valid_until >= ?)
                ORDER BY price, lead_time_days LIMIT 1""", itemId, Date.valueOf(on), Date.valueOf(on)).stream().findFirst().orElse(Map.of());
    }

    // ------------------------------------------------------------------ dipakai Procurement

    public List<Suggestion> suggestions(List<Long> lineIds) {
        if (lineIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT id, run_id, item_id, planned_qty, need_date, partner_id, est_price FROM scm.mrp_line
                WHERE action = 'BUY' AND pr_line_id IS NULL AND id IN (%s) ORDER BY id"""
                        .formatted(String.join(",", lineIds.stream().map(x -> "?").toList())),
                (rs, i) -> new Suggestion(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getBigDecimal(4), rs.getDate(5).toLocalDate(),
                        (Long) rs.getObject(6), rs.getBigDecimal(7)), lineIds.toArray());
    }

    @Transactional
    public void markRequested(Long mrpLineId, Long prLineId) {
        jdbc.update("UPDATE scm.mrp_line SET pr_line_id = ? WHERE id = ?", prLineId, mrpLineId);
    }
}
