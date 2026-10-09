package id.herbatech.erp.fin;

import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.document.DocumentWorkflowService;
import id.herbatech.erp.shared.domain.DocStatus;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.security.UserDirectory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

interface CashVoucherRepository extends DocumentRepository<CashVoucher> {
}

/** Penanda "melebihi anggaran" untuk dokumen biaya (PRD FIN aturan 4 & FIN-51). */
@Component
class BudgetGate {

    private final BudgetService budgets;

    BudgetGate(BudgetService budgets) {
        this.budgets = budgets;
    }

    record Cost(Long costCenterId, Long accountId, BigDecimal amount) {
    }

    /** @return true bila ada baris yang melebihi sisa anggaran (mode WARN); melempar error pada mode BLOCK. */
    boolean overBudget(Long plantId, LocalDate date, List<Cost> costs) {
        String mode = budgets.mode();
        if ("OFF".equals(mode)) {
            return false;
        }
        Map<String, Cost> grouped = new LinkedHashMap<>();
        for (Cost c : costs) {
            if (c.costCenterId() == null || !budgets.isControlled(c.accountId())) {
                continue;
            }
            grouped.merge(c.costCenterId() + ":" + c.accountId(), c,
                    (a, b) -> new Cost(a.costCenterId(), a.accountId(), a.amount().add(b.amount())));
        }
        boolean over = false;
        for (Cost c : grouped.values()) {
            BudgetService.Check chk = budgets.check(plantId, c.costCenterId(), c.accountId(), date, c.amount());
            if (chk.exceeded()) {
                if ("BLOCK".equals(mode)) {
                    throw BusinessException.of("OVER_BUDGET", "Melebihi anggaran: sisa Rp %,.0f, diminta Rp %,.0f",
                            chk.available(), c.amount());
                }
                over = true;
            }
        }
        return over;
    }
}

@Component
class CashVoucherHandler implements DocumentHandler<CashVoucher> {

    private final CashVoucherRepository repo;
    private final JournalPostingService journals;
    private final FinSupport fin;
    private final BudgetGate budget;
    private final UserDirectory users;
    private final JdbcTemplate jdbc;

    CashVoucherHandler(CashVoucherRepository repo, JournalPostingService journals, FinSupport fin, BudgetGate budget,
                       UserDirectory users, JdbcTemplate jdbc) {
        this.repo = repo;
        this.journals = journals;
        this.fin = fin;
        this.budget = budget;
        this.users = users;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "KK"; }
    @Override public String periodModule() { return "FIN"; }
    @Override public CashVoucher load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Voucher kas", id)); }
    @Override public CashVoucher save(CashVoucher doc) { return repo.save(doc); }

    @Override
    public String summary(CashVoucher d) {
        String kind = switch (d.getKind()) {
            case "ADVANCE" -> "Uang muka";
            case "SETTLEMENT" -> "Pertanggungjawaban";
            default -> "Kas kecil";
        };
        return kind + " · " + d.getDescription();
    }

    @Override public BigDecimal amount(CashVoucher d) { return d.getAmount(); }

    @Override
    public Long requesterId(CashVoucher d) {
        Long u = d.getEmployeeId() == null ? null : users.userIdOfEmployee(d.getEmployeeId());
        return u != null ? u : d.getCreatedBy();
    }

    @Override
    public Set<String> approvalFlags(CashVoucher d) {
        if ("ADVANCE".equals(d.getKind())) {
            return Set.of();
        }
        List<BudgetGate.Cost> costs = d.getLines().stream()
                .map(l -> new BudgetGate.Cost(l.getCostCenterId(), l.getAccountId(), l.getAmount())).toList();
        return budget.overBudget(d.getPlantId(), d.getDocDate(), costs) ? Set.of("OVER_BUDGET") : Set.of();
    }

    @Override
    public void validateSubmit(CashVoucher d) {
        fin.glAccountOfBank(d.getCashAccountId());
        switch (d.getKind()) {
            case "ADVANCE" -> {
                if (d.getEmployeeId() == null) {
                    throw new BusinessException("KK_EMP", "Uang muka wajib menyebut karyawan penerima");
                }
                if (d.getAmount().signum() <= 0) {
                    throw new BusinessException("KK_AMOUNT", "Nilai uang muka harus lebih dari nol");
                }
            }
            case "SETTLEMENT" -> {
                CashVoucher adv = advance(d);
                if (adv.getSettledAmount().signum() > 0) {
                    throw new BusinessException("KK_SETTLED", "Uang muka " + adv.getDocNo() + " sudah dipertanggungjawabkan");
                }
                validateLines(d);
            }
            default -> validateLines(d);
        }
    }

    private void validateLines(CashVoucher d) {
        if (d.getLines().isEmpty()) {
            throw new BusinessException("KK_LINES", "Isi minimal satu baris biaya");
        }
        for (CashVoucher.Line l : d.getLines()) {
            fin.validateAccount(l.getAccountId(), l.getCostCenterId(), "Baris " + l.getLineNo());
        }
    }

    private CashVoucher advance(CashVoucher d) {
        if (d.getAdvanceId() == null) {
            throw new BusinessException("KK_ADV", "Pilih uang muka yang dipertanggungjawabkan");
        }
        CashVoucher adv = load(d.getAdvanceId());
        if (!"ADVANCE".equals(adv.getKind()) || adv.getStatus() != DocStatus.POSTED) {
            throw new BusinessException("KK_ADV", "Uang muka " + adv.getDocNo() + " belum dicairkan (diposting)");
        }
        return adv;
    }

    @Override
    public void onPost(CashVoucher d) {
        validateSubmit(d);
        Long cashGl = fin.glAccountOfBank(d.getCashAccountId());
        List<JournalPostingService.IdLine> lines = new ArrayList<>();
        switch (d.getKind()) {
            case "ADVANCE" -> {
                lines.add(new JournalPostingService.IdLine(journals.accountId("1230"), null, d.getDescription(), d.getAmount(), null));
                lines.add(new JournalPostingService.IdLine(cashGl, null, d.getDescription(), null, d.getAmount()));
            }
            case "SETTLEMENT" -> {
                CashVoucher adv = advance(d);
                d.getLines().forEach(l -> lines.add(new JournalPostingService.IdLine(l.getAccountId(), l.getCostCenterId(),
                        l.getDescription(), l.getAmount(), null)));
                lines.add(new JournalPostingService.IdLine(journals.accountId("1230"), null, "Pertanggungjawaban " + adv.getDocNo(),
                        null, adv.getAmount()));
                BigDecimal diff = adv.getAmount().subtract(d.getAmount());
                if (diff.signum() > 0) {
                    lines.add(new JournalPostingService.IdLine(cashGl, null, "Pengembalian sisa uang muka", diff, null));
                } else if (diff.signum() < 0) {
                    lines.add(new JournalPostingService.IdLine(cashGl, null, "Kekurangan uang muka dibayarkan", null, diff.negate()));
                }
                adv.setSettledAmount(d.getAmount());
                repo.save(adv);
            }
            default -> {
                d.getLines().forEach(l -> lines.add(new JournalPostingService.IdLine(l.getAccountId(), l.getCostCenterId(),
                        l.getDescription(), l.getAmount(), null)));
                lines.add(new JournalPostingService.IdLine(cashGl, null, d.getDescription(), null, d.getAmount()));
            }
        }
        journals.postIds(d.getPlantId(), d.getDocDate(), lines, "KK", d.getId(), d.getDocNo(), summary(d));
    }

    @Override
    public CashVoucher reverse(CashVoucher d, LocalDate date, String reason) {
        if ("ADVANCE".equals(d.getKind()) && d.getSettledAmount().signum() > 0) {
            throw new BusinessException("KK_SETTLED", "Batalkan dulu pertanggungjawaban uang muka ini");
        }
        journals.reverseFor("KK", d.getId(), date, reason);
        if ("SETTLEMENT".equals(d.getKind()) && d.getAdvanceId() != null) {
            CashVoucher adv = load(d.getAdvanceId());
            adv.setSettledAmount(BigDecimal.ZERO);
            repo.save(adv);
        }
        return d;
    }

    String employeeName(Long id) {
        return id == null ? null : jdbc.queryForList("SELECT nik || ' · ' || name FROM hc.employee WHERE id = ?", String.class, id)
                .stream().findFirst().orElse(null);
    }
}

/** FIN-30 API kas kecil & uang muka kerja. */
@RestController
@RequestMapping("/api/fin/cash-vouchers")
class CashVoucherController extends DocumentApi<CashVoucher> {

    private final CashVoucherHandler cv;
    private final JdbcTemplate jdbc;

    CashVoucherController(CashVoucherHandler handler, CashVoucherRepository repo, Support support, JdbcTemplate jdbc) {
        super(handler, repo, support, CashVoucher.class);
        this.cv = handler;
        this.jdbc = jdbc;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "description", "sourceDocNo");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("settledAmount", "sourceDocNo");
    }

    @Override
    protected void apply(CashVoucher target, CashVoucher in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (CashVoucher.Line l : in.getLines()) {
            l.setId(null);
            l.setVoucher(target);
            l.setLineNo(no++);
            target.getLines().add(l);
        }
    }

    @Override
    protected void beforeSave(CashVoucher d, boolean isNew) {
        require(Set.of("EXPENSE", "ADVANCE", "SETTLEMENT").contains(d.getKind()), "KK_KIND", "Jenis voucher tidak dikenal");
        require(d.getCashAccountId() != null, "KK_CASH", "Pilih rekening kas/bank");
        require(d.getDescription() != null && !d.getDescription().isBlank(), "KK_DESC", "Uraian wajib diisi");
        if ("ADVANCE".equals(d.getKind())) {
            d.getLines().clear();
        } else {
            for (CashVoucher.Line l : d.getLines()) {
                require(l.getAmount() != null && l.getAmount().signum() > 0, "KK_LINE", "Nilai setiap baris harus lebih dari nol");
            }
            d.setAmount(d.getLines().stream().map(CashVoucher.Line::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        if ("SETTLEMENT".equals(d.getKind()) && d.getAdvanceId() != null) {
            CashVoucher adv = cv.load(d.getAdvanceId());
            d.setEmployeeId(adv.getEmployeeId());
        }
    }

    @Override
    protected void enrich(Map<String, Object> body, CashVoucher d) {
        body.put("employeeName", cv.employeeName(d.getEmployeeId()));
        body.put("cashAccountName", jdbc.queryForList("SELECT code || ' · ' || name FROM fin.bank_account WHERE id = ?", String.class,
                d.getCashAccountId()).stream().findFirst().orElse(null));
        if (d.getAdvanceId() != null) {
            Map<String, Object> adv = jdbc.queryForMap("SELECT doc_no, amount FROM fin.cash_voucher WHERE id = ?", d.getAdvanceId());
            body.put("advanceDocNo", adv.get("doc_no"));
            body.put("advanceAmount", adv.get("amount"));
        }
        if ("ADVANCE".equals(d.getKind())) {
            body.put("outstanding", d.getStatus() == DocStatus.POSTED && d.getSettledAmount().signum() == 0 ? d.getAmount() : BigDecimal.ZERO);
        }
    }
}

