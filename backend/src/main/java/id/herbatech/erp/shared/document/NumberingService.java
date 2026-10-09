package id.herbatech.erp.shared.document;

import id.herbatech.erp.shared.error.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Penomoran dokumen (PRD §13): {@code [KODE]/[PLANT]/[YYMM]/[urut 5 digit]}, mis. {@code PO/P1/2610/00042}.
 * Counter dinaikkan dengan satu UPSERT atomik (baris terkunci sampai commit), jadi aman dipakai paralel.
 * Nomor yang sudah dipakai dokumen tidak pernah dipakai ulang walau dokumennya dibatalkan.
 */
@Service
public class NumberingService {

    private static final DateTimeFormatter YYMM = DateTimeFormatter.ofPattern("yyMM");
    private static final DateTimeFormatter YY = DateTimeFormatter.ofPattern("yy");

    private final JdbcTemplate jdbc;
    private final DocTypeRepository docTypes;
    private final Map<Long, String> plantCodes = new ConcurrentHashMap<>();

    public NumberingService(JdbcTemplate jdbc, DocTypeRepository docTypes) {
        this.jdbc = jdbc;
        this.docTypes = docTypes;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(String docTypeCode, Long plantId, LocalDate date) {
        DocType type = docTypes.findByCode(docTypeCode)
                .orElseThrow(() -> new BusinessException("DOC_TYPE", "Jenis dokumen " + docTypeCode + " belum dikonfigurasi di SYS-05"));
        if (!type.isActive()) {
            throw new BusinessException("DOC_TYPE", "Jenis dokumen " + docTypeCode + " tidak aktif");
        }
        String plant = plantCode(plantId);
        String period = switch (type.getResetPeriod()) {
            case "YEARLY" -> date.format(YY);
            case "NEVER" -> "0000";
            default -> date.format(YYMM);
        };
        Integer no = jdbc.queryForObject("""
                INSERT INTO core.doc_sequence (doc_code, plant_code, period, last_no) VALUES (?, ?, ?, 1)
                ON CONFLICT (doc_code, plant_code, period) DO UPDATE SET last_no = core.doc_sequence.last_no + 1
                RETURNING last_no""", Integer.class, docTypeCode, plant, period);
        String periodPart = "NEVER".equals(type.getResetPeriod()) ? "" : period + "/";
        return "%s/%s/%s%05d".formatted(type.getPrefix(), plant, periodPart, no);
    }

    /**
     * Nomor batch produksi: {@code [kode produk][YY][MM][urut]}, mis. {@code HB0126100007}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextBatchNo(String productPrefix, LocalDate date) {
        String period = date.format(YYMM);
        Integer no = jdbc.queryForObject("""
                INSERT INTO core.doc_sequence (doc_code, plant_code, period, last_no) VALUES (?, 'BATCH', ?, 1)
                ON CONFLICT (doc_code, plant_code, period) DO UPDATE SET last_no = core.doc_sequence.last_no + 1
                RETURNING last_no""", Integer.class, "B:" + productPrefix, period);
        return "%s%s%04d".formatted(productPrefix, period, no);
    }

    public String plantCode(Long plantId) {
        if (plantId == null) {
            throw new BusinessException("PLANT", "Plant aktif belum dipilih");
        }
        return plantCodes.computeIfAbsent(plantId, id -> jdbc.queryForList(
                        "SELECT code FROM sys.plant WHERE id = ?", String.class, id).stream().findFirst()
                .orElseThrow(() -> new BusinessException("PLANT", "Plant " + id + " tidak ditemukan")));
    }
}
