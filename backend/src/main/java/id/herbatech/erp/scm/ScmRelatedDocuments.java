package id.herbatech.erp.scm;

import id.herbatech.erp.shared.document.RelatedDocumentProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rantai dokumen Procure to Pay & Order to Cash untuk panel "Dokumen terkait":
 * PR → RFQ → PO → GR/BAST → faktur supplier; SO → surat jalan → faktur penjualan → retur; WO → permintaan/retur bahan → hasil produksi.
 */
@Component
class ScmRelatedDocuments implements RelatedDocumentProvider {

    private static final Map<String, String> SQL = Map.ofEntries(
            Map.entry("PR", """
                    SELECT DISTINCT 'PO', l.po_id, 'Purchase order' FROM prc.po_line l JOIN prc.pr_line p ON p.id = l.pr_line_id WHERE p.pr_id = ?
                    UNION ALL SELECT 'RFQ', id, 'RFQ' FROM prc.rfq WHERE pr_id = ?"""),
            Map.entry("RFQ", """
                    SELECT 'PO', po_id, 'PO pemenang' FROM prc.rfq WHERE id = ? AND po_id IS NOT NULL
                    UNION ALL SELECT 'PR', pr_id, 'PR asal' FROM prc.rfq WHERE id = ? AND pr_id IS NOT NULL"""),
            Map.entry("PO", """
                    SELECT DISTINCT 'PR', p.pr_id, 'PR asal' FROM prc.po_line l JOIN prc.pr_line p ON p.id = l.pr_line_id WHERE l.po_id = ?
                    UNION ALL SELECT 'GR', id, 'Penerimaan barang' FROM scm.gr WHERE po_id = ?
                    UNION ALL SELECT 'BAST', id, 'BAST jasa' FROM prc.bast WHERE po_id = ?
                    UNION ALL SELECT 'INV-AP', id, 'Faktur supplier' FROM fin.ap_invoice WHERE po_id = ?
                    UNION ALL SELECT 'LC', id, 'Landed cost' FROM prc.landed_cost WHERE po_id = ?
                    UNION ALL SELECT 'RTS', id, 'Retur supplier' FROM prc.supplier_return WHERE po_id = ?
                    UNION ALL SELECT 'RFQ', rfq_id, 'RFQ' FROM prc.po WHERE id = ? AND rfq_id IS NOT NULL"""),
            Map.entry("GR", "SELECT 'PO', po_id, 'Purchase order' FROM scm.gr WHERE id = ?"),
            Map.entry("BAST", "SELECT 'PO', po_id, 'Purchase order' FROM prc.bast WHERE id = ?"),
            Map.entry("LC", "SELECT 'PO', po_id, 'Purchase order' FROM prc.landed_cost WHERE id = ?"),
            Map.entry("RTS", "SELECT 'PO', po_id, 'Purchase order' FROM prc.supplier_return WHERE id = ? AND po_id IS NOT NULL"),
            Map.entry("INV-AP", "SELECT 'PO', po_id, 'Purchase order' FROM fin.ap_invoice WHERE id = ? AND po_id IS NOT NULL"),
            Map.entry("SO", "SELECT 'DO', id, 'Surat jalan' FROM scm.delivery WHERE so_id = ?"),
            Map.entry("DO", """
                    SELECT 'SO', so_id, 'Pesanan' FROM scm.delivery WHERE id = ?
                    UNION ALL SELECT 'INV-AR', ar_invoice_id, 'Faktur penjualan' FROM scm.delivery WHERE id = ? AND ar_invoice_id IS NOT NULL
                    UNION ALL SELECT 'CRT', id, 'Retur pelanggan' FROM scm.customer_return WHERE delivery_id = ?"""),
            Map.entry("INV-AR", "SELECT 'DO', delivery_id, 'Surat jalan' FROM fin.ar_invoice WHERE id = ? AND delivery_id IS NOT NULL"),
            Map.entry("CRT", "SELECT 'DO', delivery_id, 'Surat jalan' FROM scm.customer_return WHERE id = ?"),
            Map.entry("WO", """
                    SELECT 'MR', id, 'Permintaan bahan' FROM pre.material_request WHERE wo_id = ?
                    UNION ALL SELECT 'MRT', id, 'Retur sisa bahan' FROM pre.material_return WHERE wo_id = ?
                    UNION ALL SELECT 'HP', id, 'Hasil produksi' FROM pre.production_output WHERE wo_id = ?"""),
            Map.entry("MR", "SELECT 'WO', wo_id, 'Work order' FROM pre.material_request WHERE id = ?"),
            Map.entry("MRT", "SELECT 'WO', wo_id, 'Work order' FROM pre.material_return WHERE id = ?"),
            Map.entry("HP", "SELECT 'WO', wo_id, 'Work order' FROM pre.production_output WHERE id = ?"));

    private final JdbcTemplate jdbc;

    ScmRelatedDocuments(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RelatedDoc> related(String docType, Long docId) {
        String sql = SQL.get(docType);
        if (sql == null) {
            return List.of();
        }
        int params = sql.split("\\?", -1).length - 1;
        Object[] args = new Object[params];
        java.util.Arrays.fill(args, docId);
        List<RelatedDoc> out = new ArrayList<>();
        jdbc.query(sql, rs -> {
            String type = rs.getString(1);
            long id = rs.getLong(2);
            String relation = rs.getString(3);
            jdbc.query("SELECT doc_no, menu_code, status FROM core.document_index WHERE doc_type = ? AND doc_id = ?", r -> {
                out.add(new RelatedDoc(type, id, r.getString(1), r.getString(2), r.getString(3), relation));
            }, type, id);
        }, args);
        return out;
    }
}
