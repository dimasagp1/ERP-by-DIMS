package id.herbatech.erp.scm;

import id.herbatech.erp.IntegrationTest;
import id.herbatech.erp.scm.InventoryService.MoveCommand;
import id.herbatech.erp.scm.InventoryService.MoveType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Aturan stok PRD §10/§13: per lot, karantina, hanya Released yang keluar, FEFO, tidak negatif. */
class InventoryServiceTest extends IntegrationTest {

    @Autowired
    InventoryService inventory;
    @Autowired
    LotRepository lots;

    Long item;
    Long quarantine;
    Long binA;
    Long binB;
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Jakarta"));

    @BeforeEach
    void ids() {
        item = id("SELECT id FROM sys.item WHERE code = 'RM-SIM-001'");
        Long wh = id("SELECT id FROM sys.warehouse WHERE code = 'WH-RM'");
        quarantine = id("SELECT id FROM sys.location WHERE warehouse_id = ? AND bin_code = 'QRN-01'", wh);
        binA = id("SELECT id FROM sys.location WHERE warehouse_id = ? AND bin_code = 'A-01-01'", wh);
        binB = id("SELECT id FROM sys.location WHERE warehouse_id = ? AND bin_code = 'A-01-02'", wh);
    }

    private Lot receive(String qty, LocalDate exp) {
        return as("scm.manager", () -> {
            Lot lot = inventory.createLot(item, "T" + UUID.randomUUID().toString().substring(0, 8), null, today, exp, "TEST");
            inventory.move(new MoveCommand(MoveType.RECEIPT, item, lot.getId(), null, quarantine, new BigDecimal(qty),
                    new BigDecimal("50000"), today, "TEST", 1L, "TEST/1", null));
            return lot;
        });
    }

    private void release(Lot lot) {
        jdbc.update("UPDATE scm.lot SET qc_status = 'RELEASED' WHERE id = ?", lot.getId());
    }

    private void putaway(Lot lot, Long bin, String qty) {
        asVoid("scm.manager", () -> inventory.move(new MoveCommand(MoveType.TRANSFER, item, lot.getId(), quarantine, bin,
                new BigDecimal(qty), null, today, "TEST", 1L, "TEST/1", null)));
    }

    @Test
    void quarantineLotCannotBeIssuedOrStoredOutsideQuarantine() {
        Lot lot = receive("100", today.plusYears(1));
        assertThatThrownBy(() -> putaway(lot, binA, "10")).hasMessageContaining("lokasi karantina");
        assertThatThrownBy(() -> asVoid("scm.manager", () -> inventory.move(new MoveCommand(MoveType.ISSUE, item, lot.getId(),
                quarantine, null, BigDecimal.ONE, null, today, "TEST", 1L, "TEST/1", null))))
                .hasMessageContaining("hanya stok Released");
    }

    @Test
    void stockNeverGoesNegative() {
        Lot lot = receive("10", today.plusYears(1));
        release(lot);
        putaway(lot, binA, "10");
        assertThatThrownBy(() -> asVoid("scm.manager", () -> inventory.move(new MoveCommand(MoveType.ISSUE, item, lot.getId(),
                binA, null, new BigDecimal("10.5"), null, today, "TEST", 1L, "TEST/1", null))))
                .hasMessageContaining("tidak boleh negatif");
        assertThat(inventory.onHand(item, lot.getId(), binA)).isEqualByComparingTo("10");
    }

    @Test
    void fefoPicksEarliestExpiryFirstAndSkipsExpired() {
        jdbc.update("UPDATE scm.lot SET qc_status = 'HOLD' WHERE item_id = ? AND qc_status = 'RELEASED'", item);
        Lot late = receive("30", today.plusMonths(12));
        Lot early = receive("20", today.plusMonths(3));
        Lot expired = receive("50", today.plusDays(1));
        for (Lot l : List.of(late, early, expired)) {
            release(l);
        }
        putaway(late, binB, "30");
        putaway(early, binA, "20");
        // Lot kedaluwarsa tidak boleh keluar dari karantina sebagai stok tersedia.
        jdbc.update("UPDATE scm.lot SET exp_date = ? WHERE id = ?", today.minusDays(1), expired.getId());

        Long wh = id("SELECT id FROM sys.warehouse WHERE code = 'WH-RM'");
        var picks = inventory.suggestFefo(item, new BigDecimal("35"), wh, today);
        assertThat(picks).extracting(InventoryService.FefoPick::lotId).containsExactly(early.getId(), late.getId());
        assertThat(picks.get(0).qty()).isEqualByComparingTo("20");
        assertThat(picks.get(1).qty()).isEqualByComparingTo("15");
    }

    @Test
    void lotTrackedItemRequiresLot() {
        assertThatThrownBy(() -> asVoid("scm.manager", () -> inventory.move(new MoveCommand(MoveType.RECEIPT, item, null,
                null, quarantine, BigDecimal.ONE, null, today, "TEST", 1L, "TEST/1", null))))
                .hasMessageContaining("wajib bergerak dengan nomor lot");
    }

    @Test
    void stockMovesAreAppendOnly() {
        receive("1", today.plusYears(1));
        assertThatThrownBy(() -> jdbc.update("UPDATE scm.stock_move SET qty = 99")).hasMessageContaining("append-only");
    }
}
