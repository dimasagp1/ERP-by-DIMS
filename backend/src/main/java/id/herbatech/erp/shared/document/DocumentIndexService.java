package id.herbatech.erp.shared.document;

import id.herbatech.erp.shared.domain.DocumentEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;

/** Menjaga {@code core.document_index} tetap sinkron dengan dokumen sumber. */
@Service
public class DocumentIndexService {

    private final JdbcTemplate jdbc;

    public DocumentIndexService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(DocType type, DocumentEntity doc, String summary, BigDecimal amount) {
        jdbc.update("""
                        INSERT INTO core.document_index
                            (doc_type, doc_id, doc_no, app_code, menu_code, plant_id, status, summary, amount, doc_date, created_by, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                        ON CONFLICT (doc_type, doc_id) DO UPDATE SET
                            status = EXCLUDED.status, summary = EXCLUDED.summary, amount = EXCLUDED.amount,
                            doc_date = EXCLUDED.doc_date, updated_at = now()""",
                type.getCode(), doc.getId(), doc.getDocNo(), type.getAppCode(), type.getMenuCode(), doc.getPlantId(),
                doc.getStatus().name(), truncate(summary), amount, Date.valueOf(doc.getDocDate()), doc.getCreatedBy());
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 255 ? s : s.substring(0, 252) + "...";
    }
}
