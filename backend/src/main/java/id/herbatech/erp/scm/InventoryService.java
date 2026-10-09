package id.herbatech.erp.scm;

import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.period.PeriodLockService;
import id.herbatech.erp.shared.security.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mesin inventory (PRD §14): semua departemen yang menyentuh barang menulis lewat kelas ini.
 * <ul>
 *   <li>Semua gerak per lot & lokasi; item lot-tracked wajib nomor lot (SCM aturan 1).</li>
 *   <li>Stok tidak boleh negatif di level lot & lokasi (PRD §13); dikunci atomik di database.</li>
 *   <li>Hanya lot Released & belum kedaluwarsa yang bisa keluar ke produksi/pelanggan (SCM aturan 3).</li>
 *   <li>Penerimaan bahan & barang jadi masuk lokasi karantina (PRE aturan 6, SCM-20).</li>
 * </ul>
 */
@Service
public class InventoryService {

    public enum MoveType { RECEIPT, ISSUE, TRANSFER, PRODUCE, CONSUME, SHIP, ADJUST, RETURN, SCRAP }

    /** Gerak yang mengeluarkan barang untuk dipakai/dikirim: wajib lot Released & tidak kedaluwarsa. */
    private static final Set<MoveType> NEEDS_RELEASED = EnumSet.of(MoveType.ISSUE, MoveType.CONSUME, MoveType.SHIP);

    public record MoveCommand(MoveType type, Long itemId, Long lotId, Long fromLocationId, Long toLocationId,
                              BigDecimal qty, BigDecimal unitCost, LocalDate date, String refDocType, Long refDocId,
                              String refDocNo, String reason) {
    }

    public record FefoPick(Long lotId, String lotNo, LocalDate expDate, Long locationId, String binCode, BigDecimal qty) {
    }

    private final JdbcTemplate jdbc;
    private final LotRepository lots;
    private final PeriodLockService periods;

    public InventoryService(JdbcTemplate jdbc, LotRepository lots, PeriodLockService periods) {
        this.jdbc = jdbc;
        this.lots = lots;
        this.periods = periods;
    }

    /** Membuat lot baru berstatus Quarantine (dari GR atau hasil produksi). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Lot createLot(Long itemId, String lotNo, String supplierLot, LocalDate mfgDate, LocalDate expDate, String sourceDoc) {
        if (lotNo == null || lotNo.isBlank()) {
            throw new BusinessException("LOT", "Nomor lot wajib diisi");
        }
        lots.findByItemIdAndLotNo(itemId, lotNo.trim()).ifPresent(l -> {
            throw new BusinessException("LOT_EXISTS", "Lot " + lotNo + " untuk item ini sudah ada");
        });
        if (expDate != null && mfgDate != null && expDate.isBefore(mfgDate)) {
            throw new BusinessException("LOT_DATE", "Tanggal kedaluwarsa tidak boleh sebelum tanggal produksi");
        }
        Lot lot = new Lot();
        lot.setItemId(itemId);
        lot.setLotNo(lotNo.trim());
        lot.setSupplierLot(supplierLot);
        lot.setMfgDate(mfgDate);
        lot.setExpDate(expDate);
        lot.setSourceDoc(sourceDoc);
        return lots.save(lot);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long move(MoveCommand c) {
        if (c.qty() == null || c.qty().signum() <= 0) {
            throw new BusinessException("QTY", "Kuantitas harus lebih dari nol");
        }
        periods.assertDateAllowed("SCM", c.date());
        Map<String, Object> item = jdbc.queryForList("SELECT code, lot_tracked, status, type FROM sys.item WHERE id = ?", c.itemId())
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Item", c.itemId()));
        boolean lotTracked = (Boolean) item.get("lot_tracked");
        String itemCode = (String) item.get("code");

        Lot lot = null;
        if (lotTracked) {
            if (c.lotId() == null) {
                throw new BusinessException("LOT_REQUIRED", "Item " + itemCode + " wajib bergerak dengan nomor lot");
            }
            lot = lots.findById(c.lotId()).orElseThrow(() -> new NotFoundException("Lot", c.lotId()));
            if (!lot.getItemId().equals(c.itemId())) {
                throw new BusinessException("LOT_ITEM", "Lot " + lot.getLotNo() + " bukan milik item " + itemCode);
            }
        } else if (c.lotId() != null) {
            throw new BusinessException("LOT_NOT_TRACKED", "Item " + itemCode + " tidak dilacak per lot");
        }

        validateLocations(c);
        if (c.type() == MoveType.RECEIPT && !"ACTIVE".equals(item.get("status"))) {
            throw new BusinessException("ITEM_STATUS", "Item " + itemCode + " berstatus " + item.get("status") + " dan tidak bisa diterima");
        }
        if (lot != null && NEEDS_RELEASED.contains(c.type())) {
            if (lot.getQcStatus() != Lot.QcStatus.RELEASED) {
                throw new BusinessException("LOT_NOT_RELEASED", "Lot " + lot.getLotNo() + " berstatus " + lot.getQcStatus()
                        + "; hanya stok Released yang bisa dipakai atau dikirim");
            }
            if (lot.isExpiredOn(c.date())) {
                throw new BusinessException("LOT_EXPIRED", "Lot " + lot.getLotNo() + " sudah kedaluwarsa (" + lot.getExpDate() + ")");
            }
        }
        if (lot != null && c.toLocationId() != null && lot.getQcStatus() != Lot.QcStatus.RELEASED
                && c.type() != MoveType.SCRAP && !isQuarantine(c.toLocationId())) {
            throw new BusinessException("LOT_QUARANTINE_ZONE", "Lot " + lot.getLotNo() + " belum Released; hanya boleh disimpan di lokasi karantina");
        }

        BigDecimal cost = c.unitCost() == null ? BigDecimal.ZERO : c.unitCost();
        if (c.fromLocationId() != null) {
            BigDecimal fromCost = take(c.itemId(), c.lotId(), c.fromLocationId(), c.qty(), itemCode, lot);
            if (c.unitCost() == null) {
                cost = fromCost;
            }
        }
        if (c.toLocationId() != null) {
            put(c.itemId(), c.lotId(), c.toLocationId(), c.qty(), cost);
        }
        return jdbc.queryForObject("""
                        INSERT INTO scm.stock_move (move_type, item_id, lot_id, from_loc_id, to_loc_id, qty, unit_cost, move_date,
                                                    ref_doc_type, ref_doc_id, ref_doc_no, reason, user_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class,
                c.type().name(), c.itemId(), c.lotId(), c.fromLocationId(), c.toLocationId(), c.qty(), cost,
                Date.valueOf(c.date()), c.refDocType(), c.refDocId(), c.refDocNo(), c.reason(),
                UserContext.currentOptional().map(u -> u.id()).orElse(null));
    }

    /**
     * Saran picking FEFO (SCM aturan 2): lot Released, belum kedaluwarsa pada tanggal pakai,
     * urut kedaluwarsa paling awal, dari lokasi non-karantina di gudang yang diminta.
     */
    public List<FefoPick> suggestFefo(Long itemId, BigDecimal qty, Long warehouseId, LocalDate onDate) {
        List<FefoPick> candidates = jdbc.query("""
                        SELECT q.lot_id, l.lot_no, l.exp_date, q.location_id, loc.bin_code, q.qty - q.qty_reserved AS avail
                        FROM scm.stock_quant q
                        JOIN scm.lot l ON l.id = q.lot_id
                        JOIN sys.location loc ON loc.id = q.location_id
                        WHERE q.item_id = ? AND loc.warehouse_id = ? AND NOT loc.is_quarantine
                          AND l.qc_status = 'RELEASED' AND (l.exp_date IS NULL OR l.exp_date >= ?)
                          AND q.qty - q.qty_reserved > 0
                        ORDER BY l.exp_date NULLS LAST, l.lot_no, loc.bin_code""",
                (rs, i) -> new FefoPick(rs.getLong(1), rs.getString(2),
                        rs.getDate(3) == null ? null : rs.getDate(3).toLocalDate(), rs.getLong(4), rs.getString(5),
                        rs.getBigDecimal(6)),
                itemId, warehouseId, Date.valueOf(onDate));
        List<FefoPick> picks = new ArrayList<>();
        BigDecimal remaining = qty;
        for (FefoPick p : candidates) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal take = p.qty().min(remaining);
            picks.add(new FefoPick(p.lotId(), p.lotNo(), p.expDate(), p.locationId(), p.binCode(), take));
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            throw BusinessException.of("STOCK_SHORT", "Stok Released tidak cukup: kurang %s", remaining.stripTrailingZeros().toPlainString());
        }
        return picks;
    }

    /** Data item yang sering dibutuhkan dokumen gudang. */
    public record ItemInfo(Long id, String code, String name, String type, boolean lotTracked, String uom, Integer shelfLifeDays,
                           String storageClass) {
    }

    public ItemInfo item(Long itemId) {
        return jdbc.query("""
                        SELECT i.id, i.code, i.name, i.type, i.lot_tracked, u.code AS uom, i.shelf_life_days, i.storage_class
                        FROM sys.item i JOIN sys.uom u ON u.id = i.uom_id WHERE i.id = ?""",
                rs -> rs.next() ? new ItemInfo(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5),
                        rs.getString(6), (Integer) rs.getObject(7), rs.getString(8)) : null, itemId);
    }

    public ItemInfo requireItem(Long itemId) {
        ItemInfo i = itemId == null ? null : item(itemId);
        if (i == null) {
            throw new BusinessException("ITEM", "Item wajib dipilih");
        }
        return i;
    }

    /** Lokasi karantina pertama di gudang plant untuk jenis item (RM/PM/FG...), untuk penerimaan & retur. */
    public Long quarantineLocation(Long plantId, String itemType) {
        return jdbc.queryForList("""
                        SELECT l.id FROM sys.location l JOIN sys.warehouse w ON w.id = l.warehouse_id
                        WHERE w.plant_id = ? AND l.is_quarantine AND l.active AND w.active
                        ORDER BY (w.type = ?) DESC, w.id, l.id LIMIT 1""", Long.class, plantId, itemType)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException("NO_QRN", "Belum ada lokasi karantina di gudang plant ini (SYS-09)"));
    }

    /** Lokasi simpan bawaan (non-karantina) untuk item yang tidak dilacak per lot, mis. sparepart & ATK. */
    public Long defaultLocation(Long plantId, String itemType) {
        return jdbc.queryForList("""
                        SELECT l.id FROM sys.location l JOIN sys.warehouse w ON w.id = l.warehouse_id
                        WHERE w.plant_id = ? AND NOT l.is_quarantine AND l.active AND w.active
                        ORDER BY (w.type = ?) DESC, (w.type = 'GENERAL') DESC, w.id, l.bin_code LIMIT 1""", Long.class, plantId,
                        "ATK".equals(itemType) ? "GENERAL" : itemType)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException("NO_LOC", "Belum ada lokasi simpan di gudang plant ini (SYS-09)"));
    }

    /**
     * Revaluasi lot (landed cost, PRC-10): tambahan biaya dibagi ke sisa stok lot sebanding qty diterima.
     * @return bagian yang terkapitalisasi; sisanya (stok sudah terpakai) dibebankan pemanggil sebagai selisih harga.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal revalueLot(Long itemId, Long lotId, BigDecimal receivedQty, BigDecimal amount) {
        BigDecimal remaining = jdbc.queryForObject("""
                SELECT COALESCE(SUM(qty), 0) FROM scm.stock_quant WHERE item_id = ? AND COALESCE(lot_id, 0) = COALESCE(?::bigint, 0)""",
                BigDecimal.class, itemId, lotId);
        if (remaining.signum() <= 0 || amount.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal base = receivedQty == null || receivedQty.signum() <= 0 ? remaining : receivedQty.max(remaining);
        BigDecimal capitalized = amount.multiply(remaining).divide(base, 2, java.math.RoundingMode.HALF_UP);
        BigDecimal perUnit = capitalized.divide(remaining, 6, java.math.RoundingMode.HALF_UP);
        jdbc.update("""
                UPDATE scm.stock_quant SET unit_cost = unit_cost + ?, updated_at = now(), version = version + 1
                WHERE item_id = ? AND COALESCE(lot_id, 0) = COALESCE(?::bigint, 0) AND qty > 0""", perUnit, itemId, lotId);
        return capitalized;
    }

    /** Biaya satuan rata-rata tertimbang stok item (opsional per lot) saat ini. */
    public BigDecimal averageCost(Long itemId, Long lotId) {
        return jdbc.queryForObject("""
                SELECT CASE WHEN SUM(qty) > 0 THEN SUM(qty * unit_cost) / SUM(qty) ELSE 0 END FROM scm.stock_quant
                WHERE item_id = ? AND (?::bigint IS NULL OR lot_id = ?)""", BigDecimal.class, itemId, lotId, lotId);
    }

    /** Biaya satuan di satu lot-lokasi (0 bila belum ada stok). */
    public BigDecimal unitCost(Long itemId, Long lotId, Long locationId) {
        return jdbc.query("""
                        SELECT unit_cost FROM scm.stock_quant WHERE item_id = ? AND COALESCE(lot_id, 0) = COALESCE(?::bigint, 0) AND location_id = ?""",
                rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO, itemId, lotId, locationId);
    }

    public BigDecimal onHand(Long itemId, Long lotId, Long locationId) {
        return jdbc.query("""
                        SELECT qty FROM scm.stock_quant WHERE item_id = ? AND COALESCE(lot_id, 0) = COALESCE(?::bigint, 0) AND location_id = ?""",
                rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO, itemId, lotId, locationId);
    }

    /** Mengurangi stok secara atomik; gagal bila tersedia (qty − reserved) kurang. Mengembalikan biaya satuan. */
    private BigDecimal take(Long itemId, Long lotId, Long locationId, BigDecimal qty, String itemCode, Lot lot) {
        List<BigDecimal> cost = jdbc.queryForList("""
                        UPDATE scm.stock_quant SET qty = qty - ?, updated_at = now(), version = version + 1
                        WHERE item_id = ? AND COALESCE(lot_id, 0) = COALESCE(?::bigint, 0) AND location_id = ?
                          AND qty - qty_reserved >= ?
                        RETURNING unit_cost""", BigDecimal.class,
                qty, itemId, lotId, locationId, qty);
        if (cost.isEmpty()) {
            BigDecimal have = onHand(itemId, lotId, locationId);
            throw BusinessException.of("STOCK_NEGATIVE", "Stok %s%s di lokasi ini hanya %s; tidak boleh negatif",
                    itemCode, lot == null ? "" : " lot " + lot.getLotNo(), have.stripTrailingZeros().toPlainString());
        }
        return cost.getFirst();
    }

    /** Menambah stok; biaya satuan dirata-rata tertimbang per lot-lokasi. */
    private void put(Long itemId, Long lotId, Long locationId, BigDecimal qty, BigDecimal unitCost) {
        jdbc.update("""
                INSERT INTO scm.stock_quant (item_id, lot_id, location_id, qty, unit_cost) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (item_id, (COALESCE(lot_id, 0)), location_id) DO UPDATE SET
                    unit_cost = CASE WHEN scm.stock_quant.qty + EXCLUDED.qty = 0 THEN EXCLUDED.unit_cost
                                     ELSE (scm.stock_quant.qty * scm.stock_quant.unit_cost + EXCLUDED.qty * EXCLUDED.unit_cost)
                                          / (scm.stock_quant.qty + EXCLUDED.qty) END,
                    qty = scm.stock_quant.qty + EXCLUDED.qty,
                    updated_at = now(), version = scm.stock_quant.version + 1""",
                itemId, lotId, locationId, qty, unitCost);
    }

    private void validateLocations(MoveCommand c) {
        boolean needFrom = switch (c.type()) {
            case ISSUE, TRANSFER, CONSUME, SHIP, SCRAP -> true;
            default -> false;
        };
        boolean needTo = switch (c.type()) {
            case RECEIPT, TRANSFER, PRODUCE -> true;
            default -> false;
        };
        if (needFrom && c.fromLocationId() == null) {
            throw new BusinessException("LOCATION", "Lokasi asal wajib diisi untuk gerak " + c.type());
        }
        if (needTo && c.toLocationId() == null) {
            throw new BusinessException("LOCATION", "Lokasi tujuan wajib diisi untuk gerak " + c.type());
        }
        if (c.fromLocationId() == null && c.toLocationId() == null) {
            throw new BusinessException("LOCATION", "Lokasi asal atau tujuan wajib diisi");
        }
        if (c.fromLocationId() != null && c.fromLocationId().equals(c.toLocationId())) {
            throw new BusinessException("LOCATION", "Lokasi asal dan tujuan tidak boleh sama");
        }
    }

    private boolean isQuarantine(Long locationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT is_quarantine FROM sys.location WHERE id = ?", Boolean.class, locationId));
    }
}
