/**
 * Supply Chain (SCM). M0: mesin inventory — satu-satunya pintu tulis stok per lot & lokasi bin
 * ({@link id.herbatech.erp.scm.InventoryService}) dan perubahan status lot oleh QA
 * ({@link id.herbatech.erp.scm.LotStatusService}). M2: PPIC (pesanan, forecast & S&OP, MPS, BOM, MRP, kapasitas),
 * warehouse (GR, putaway, transfer, surat jalan, retur, pengeluaran non-produksi), inventory control (opname,
 * penyesuaian, parameter stok, kedaluwarsa, penelusuran lot, pemusnahan).
 */
@ApplicationModule(displayName = "Supply Chain")
package id.herbatech.erp.scm;

import org.springframework.modulith.ApplicationModule;
