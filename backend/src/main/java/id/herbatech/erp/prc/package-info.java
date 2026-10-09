/**
 * Procurement (PRC). M2: purchase requisition (PRC-02, ESS-04), supplier & ASL (PRC-03/04), RFQ & perbandingan
 * penawaran (PRC-05/06), purchase order dengan komitmen anggaran (PRC-07), kontrak harga (PRC-08), monitoring
 * kedatangan (PRC-09), impor & landed cost (PRC-10), retur & klaim supplier (PRC-11), jasa & BAST (PRC-12),
 * penilaian kinerja supplier (PRC-13). Penerimaan fisik di SCM-20 memperbarui PO lewat {@link id.herbatech.erp.scm.GoodsReceived}.
 */
@ApplicationModule(displayName = "Procurement")
package id.herbatech.erp.prc;

import org.springframework.modulith.ApplicationModule;
