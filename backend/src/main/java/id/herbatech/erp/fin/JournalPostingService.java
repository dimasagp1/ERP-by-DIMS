package id.herbatech.erp.fin;

import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.document.DocType;
import id.herbatech.erp.shared.document.DocTypeRepository;
import id.herbatech.erp.shared.document.DocumentIndexService;
import id.herbatech.erp.shared.document.NumberingService;
import id.herbatech.erp.shared.domain.DocStatus;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.period.PeriodLockService;
import id.herbatech.erp.shared.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Jurnal otomatis (PRD §6: "Finance tidak menginput ulang transaksi operasional"). Modul lain memanggil
 * layanan ini saat dokumennya diposting (GR, pemakaian bahan, hasil produksi, payroll, pengiriman).
 * Jurnal langsung berstatus Diposting dan tidak bisa diedit; koreksi lewat {@link #reverseFor}.
 */
@Service
public class JournalPostingService {

    /** Satu baris jurnal otomatis dengan id akun. */
    public record IdLine(Long accountId, Long costCenterId, String description, BigDecimal debit, BigDecimal credit) {
    }

    /** Satu baris jurnal otomatis. */
    public record AutoLine(String accountCode, Long costCenterId, String description, BigDecimal debit, BigDecimal credit) {
    }

    private final JournalEntryRepository journals;
    private final AccountMappingRepository mappings;
    private final AccountRepository accounts;
    private final JournalHandler handler;
    private final NumberingService numbering;
    private final PeriodLockService periods;
    private final DocumentIndexService index;
    private final DocTypeRepository docTypes;
    private final TimeService time;

    JournalPostingService(JournalEntryRepository journals, AccountMappingRepository mappings, AccountRepository accounts,
                          JournalHandler handler, NumberingService numbering, PeriodLockService periods,
                          DocumentIndexService index, DocTypeRepository docTypes, TimeService time) {
        this.journals = journals;
        this.mappings = mappings;
        this.accounts = accounts;
        this.handler = handler;
        this.numbering = numbering;
        this.periods = periods;
        this.index = index;
        this.docTypes = docTypes;
        this.time = time;
    }

    /** Jurnal dua baris dari mapping akun FIN-99 (mis. {@code GR_RM}). */
    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntry post(String txnType, Long plantId, LocalDate date, BigDecimal amount, Long costCenterId,
                             String sourceDocType, Long sourceDocId, String sourceDocNo, String description) {
        AccountMapping m = mappings.findByTxnTypeAndActiveTrue(txnType)
                .orElseThrow(() -> new BusinessException("MAPPING", "Mapping akun untuk transaksi " + txnType + " belum diatur (FIN-99)"));
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        List<JournalLine> lines = List.of(
                JournalLine.of(m.getDebitAccountId(), costCenterId, description, value, BigDecimal.ZERO),
                JournalLine.of(m.getCreditAccountId(), costCenterId, description, BigDecimal.ZERO, value));
        return create(plantId, date, lines, sourceDocType, sourceDocId, sourceDocNo, description);
    }

    /** Jurnal multi-baris dengan kode akun eksplisit (mis. payroll per cost center). */
    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntry postLines(Long plantId, LocalDate date, List<AutoLine> autoLines, String sourceDocType,
                                  Long sourceDocId, String sourceDocNo, String description) {
        List<JournalLine> lines = new ArrayList<>();
        for (AutoLine a : autoLines) {
            Account acc = accounts.findByCode(a.accountCode())
                    .orElseThrow(() -> new BusinessException("ACCOUNT", "Akun " + a.accountCode() + " tidak ditemukan"));
            lines.add(JournalLine.of(acc.getId(), a.costCenterId(), a.description(), a.debit(), a.credit()));
        }
        return create(plantId, date, lines, sourceDocType, sourceDocId, sourceDocNo, description);
    }

    /** Jurnal multi-baris dengan id akun (baris bernilai nol diabaikan). */
    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntry postIds(Long plantId, LocalDate date, List<IdLine> idLines, String sourceDocType,
                                Long sourceDocId, String sourceDocNo, String description) {
        List<JournalLine> lines = idLines.stream()
                .filter(l -> (l.debit() != null && l.debit().signum() != 0) || (l.credit() != null && l.credit().signum() != 0))
                .map(l -> JournalLine.of(l.accountId(), l.costCenterId(), l.description(),
                        nz(l.debit()).setScale(2, RoundingMode.HALF_UP), nz(l.credit()).setScale(2, RoundingMode.HALF_UP)))
                .toList();
        return create(plantId, date, lines, sourceDocType, sourceDocId, sourceDocNo, description);
    }

    /** Id akun dari kode (untuk akun sistem seperti 1230 uang muka karyawan). */
    public Long accountId(String code) {
        return accounts.findByCode(code).orElseThrow(() -> new BusinessException("ACCOUNT", "Akun " + code + " tidak ditemukan")).getId();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Membalik semua jurnal otomatis milik dokumen sumber yang dibatalkan. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<JournalEntry> reverseFor(String sourceDocType, Long sourceDocId, LocalDate date, String reason) {
        periods.assertDateAllowed("FIN", date);
        List<JournalEntry> out = new ArrayList<>();
        for (JournalEntry je : journals.findBySourceDocTypeAndSourceDocIdOrderById(sourceDocType, sourceDocId)) {
            if (je.getStatus() == DocStatus.POSTED && je.getReversalOfId() == null && je.getReversedById() == null) {
                JournalEntry full = handler.load(je.getId());
                JournalEntry rev = handler.reversalOf(full, date, "Reversal " + full.getDocNo() + ": " + reason);
                full.setStatus(DocStatus.CANCELLED);
                full.setCancelReason(reason);
                full.setCancelledAt(time.now());
                full.setCancelledBy(UserContext.currentOptional().map(u -> u.id()).orElse(null));
                handler.save(full);
                index(full);
                index(rev);
                out.add(rev);
            }
        }
        return out;
    }

    private JournalEntry create(Long plantId, LocalDate date, List<JournalLine> lines, String sourceDocType,
                                Long sourceDocId, String sourceDocNo, String description) {
        periods.assertDateAllowed("FIN", date);
        JournalEntry je = new JournalEntry();
        je.setPlantId(plantId);
        je.setDocDate(date);
        je.setDocNo(numbering.next(JournalHandler.DOC_TYPE, plantId, date));
        je.setDescription(description.length() > 255 ? description.substring(0, 255) : description);
        je.setAutoGenerated(true);
        je.setSourceDocType(sourceDocType);
        je.setSourceDocId(sourceDocId);
        je.setSourceDocNo(sourceDocNo);
        je.replaceLines(lines);
        handler.validateLines(je);
        je.setStatus(DocStatus.POSTED);
        je.setPostedAt(time.now());
        je.setPostedBy(UserContext.currentOptional().map(u -> u.id()).orElse(null));
        JournalEntry saved = journals.save(je);
        index(saved);
        return saved;
    }

    private void index(JournalEntry je) {
        DocType type = docTypes.findByCode(JournalHandler.DOC_TYPE).orElseThrow();
        index.upsert(type, je, je.getDescription(), je.getTotalDebit());
    }
}
