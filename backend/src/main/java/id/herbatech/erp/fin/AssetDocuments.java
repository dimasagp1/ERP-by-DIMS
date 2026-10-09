package id.herbatech.erp.fin;

import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.domain.DocStatus;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.web.MasterController;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

interface FixedAssetRepository extends DocumentRepository<FixedAsset> {
    List<FixedAsset> findByPlantIdAndStatusAndAssetState(Long plantId, DocStatus status, String state);
}

interface DepreciationRunRepository extends DocumentRepository<DepreciationRun> {
}

/**
 * Perhitungan penyusutan.
 * Komersial: garis lurus = (harga − residu) / umur; saldo menurun ganda = nilai buku × 2 / umur, tidak di bawah residu.
 * Fiskal (UU PPh Pasal 11, garis lurus): Kelompok 1 = 4 th, 2 = 8 th, 3 = 16 th, 4 = 20 th,
 * bangunan permanen 20 th, tidak permanen 10 th.
 */
@Service
class AssetService {

    static final Map<String, Integer> FISCAL_YEARS = Map.of("KEL1", 4, "KEL2", 8, "KEL3", 16, "KEL4", 20,
            "BANGUNAN_P", 20, "BANGUNAN_NP", 10);
    static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    record Line(FixedAsset asset, BigDecimal amount, BigDecimal fiscal) {
    }

    private final FixedAssetRepository assets;
    private final JdbcTemplate jdbc;

    AssetService(FixedAssetRepository assets, JdbcTemplate jdbc) {
        this.assets = assets;
        this.jdbc = jdbc;
    }

    static BigDecimal monthly(FixedAsset a) {
        BigDecimal base = a.getAcquisitionCost().subtract(a.getResidualValue());
        BigDecimal remaining = base.subtract(a.getAccumulated());
        if (remaining.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal amt = "DDB".equals(a.getMethod())
                ? a.bookValue().multiply(BigDecimal.valueOf(2)).divide(BigDecimal.valueOf(a.getUsefulLifeMonths()), 0, RoundingMode.HALF_UP)
                : base.divide(BigDecimal.valueOf(a.getUsefulLifeMonths()), 0, RoundingMode.HALF_UP);
        return amt.min(remaining);
    }

    static BigDecimal fiscalMonthly(FixedAsset a, String fiscalGroup) {
        Integer years = FISCAL_YEARS.get(fiscalGroup);
        if (years == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal remaining = a.getAcquisitionCost().subtract(a.getFiscalAccumulated());
        if (remaining.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return a.getAcquisitionCost().divide(BigDecimal.valueOf(years * 12L), 0, RoundingMode.HALF_UP).min(remaining);
    }

    List<Line> compute(Long plantId, YearMonth period) {
        List<Line> out = new ArrayList<>();
        for (FixedAsset a : assets.findByPlantIdAndStatusAndAssetState(plantId, DocStatus.POSTED, "ACTIVE")) {
            if (YearMonth.from(a.getDepreciationStart()).isAfter(period)) {
                continue;
            }
            String group = jdbc.queryForObject("SELECT fiscal_group FROM fin.asset_category WHERE id = ?", String.class, a.getCategoryId());
            BigDecimal amt = monthly(a);
            BigDecimal fis = fiscalMonthly(a, group);
            if (amt.signum() > 0 || fis.signum() > 0) {
                out.add(new Line(a, amt, fis));
            }
        }
        return out;
    }
}

// =====================================================================================================================
// Kapitalisasi aset
// =====================================================================================================================

@Component
class FixedAssetHandler implements DocumentHandler<FixedAsset> {

    private final FixedAssetRepository repo;
    private final JournalPostingService journals;
    private final FinSupport fin;
    private final JdbcTemplate jdbc;

    FixedAssetHandler(FixedAssetRepository repo, JournalPostingService journals, FinSupport fin, JdbcTemplate jdbc) {
        this.repo = repo;
        this.journals = journals;
        this.fin = fin;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "AST"; }
    @Override public String periodModule() { return "FIN"; }
    @Override public FixedAsset load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Aset", id)); }
    @Override public FixedAsset save(FixedAsset doc) { return repo.save(doc); }
    @Override public String summary(FixedAsset d) { return "Aset " + d.getName(); }
    @Override public BigDecimal amount(FixedAsset d) { return d.getAcquisitionCost(); }

    @Override
    public void validateSubmit(FixedAsset d) {
        if (d.getAcquisitionCost() == null || d.getAcquisitionCost().signum() <= 0) {
            throw new BusinessException("AST_COST", "Harga perolehan harus lebih dari nol");
        }
        if (d.getResidualValue().compareTo(d.getAcquisitionCost()) >= 0) {
            throw new BusinessException("AST_RESIDUAL", "Nilai residu harus lebih kecil dari harga perolehan");
        }
        fin.validateAccount(d.getCreditAccountId(), null, "Akun lawan kapitalisasi");
    }

    @Override
    public void onPost(FixedAsset d) {
        Long assetAccount = jdbc.queryForObject("SELECT asset_account_id FROM fin.asset_category WHERE id = ?", Long.class, d.getCategoryId());
        String desc = "Kapitalisasi " + d.getDocNo() + " · " + d.getName();
        journals.postIds(d.getPlantId(), d.getDocDate(), List.of(
                new JournalPostingService.IdLine(assetAccount, d.getCostCenterId(), desc, d.getAcquisitionCost(), null),
                new JournalPostingService.IdLine(d.getCreditAccountId(), null, desc, null, d.getAcquisitionCost())),
                "AST", d.getId(), d.getDocNo(), desc);
    }

    @Override
    public FixedAsset reverse(FixedAsset d, LocalDate date, String reason) {
        if (d.getAccumulated().signum() > 0) {
            throw new BusinessException("AST_DEP", "Aset sudah disusutkan; batalkan dengan proses disposal");
        }
        journals.reverseFor("AST", d.getId(), date, reason);
        return d;
    }
}

@RestController
@RequestMapping("/api/fin/assets")
class FixedAssetController extends DocumentApi<FixedAsset> {

    private final JdbcTemplate jdbc;

    FixedAssetController(FixedAssetHandler handler, FixedAssetRepository repo, Support support, JdbcTemplate jdbc) {
        super(handler, repo, support, FixedAsset.class);
        this.jdbc = jdbc;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "name", "serialNo", "location");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("accumulated", "fiscalAccumulated", "assetState");
    }

    @Override
    protected void beforeSave(FixedAsset d, boolean isNew) {
        require(d.getName() != null && !d.getName().isBlank(), "AST_NAME", "Nama aset wajib diisi");
        require(d.getCategoryId() != null, "AST_CAT", "Kategori aset wajib dipilih");
        require(d.getCostCenterId() != null, "AST_CC", "Cost center pemakai aset wajib diisi (dasar beban penyusutan)");
        Map<String, Object> cat = jdbc.queryForMap("SELECT useful_life_months, method FROM fin.asset_category WHERE id = ?", d.getCategoryId());
        if (d.getUsefulLifeMonths() <= 0) {
            d.setUsefulLifeMonths((Integer) cat.get("useful_life_months"));
        }
        if (d.getMethod() == null) {
            d.setMethod((String) cat.get("method"));
        }
        if (d.getResidualValue() == null) {
            d.setResidualValue(BigDecimal.ZERO);
        }
        if (d.getDepreciationStart() == null) {
            d.setDepreciationStart(d.getDocDate().withDayOfMonth(1).plusMonths(1));
        }
        if (d.getCreditAccountId() == null) {
            d.setCreditAccountId(jdbc.queryForObject("SELECT id FROM fin.account WHERE code = '2102'", Long.class));
        }
    }

    @Override
    protected void enrich(Map<String, Object> body, FixedAsset d) {
        body.put("bookValue", d.bookValue());
        body.put("categoryName", jdbc.queryForList("SELECT name FROM fin.asset_category WHERE id = ?", String.class, d.getCategoryId())
                .stream().findFirst().orElse(null));
        body.put("monthlyDepreciation", d.getStatus() == DocStatus.POSTED ? AssetService.monthly(d) : null);
        body.put("ageMonths", ChronoUnit.MONTHS.between(d.getDepreciationStart().withDayOfMonth(1), LocalDate.now().withDayOfMonth(1)));
    }
}

// =====================================================================================================================
// Penyusutan bulanan
// =====================================================================================================================

@Component
class DepreciationHandler implements DocumentHandler<DepreciationRun> {

    private final DepreciationRunRepository repo;
    private final FixedAssetRepository assets;
    private final AssetService service;
    private final JournalPostingService journals;
    private final JdbcTemplate jdbc;

    DepreciationHandler(DepreciationRunRepository repo, FixedAssetRepository assets, AssetService service,
                        JournalPostingService journals, JdbcTemplate jdbc) {
        this.repo = repo;
        this.assets = assets;
        this.service = service;
        this.journals = journals;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "DEP"; }
    @Override public String periodModule() { return "FIN"; }
    @Override public DepreciationRun load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Penyusutan", id)); }
    @Override public DepreciationRun save(DepreciationRun doc) { return repo.save(doc); }
    @Override public String summary(DepreciationRun d) { return "Penyusutan " + d.getPeriod() + " · " + d.getAssetCount() + " aset"; }
    @Override public BigDecimal amount(DepreciationRun d) { return d.getTotalAmount(); }

    @Transactional
    void calculate(DepreciationRun d) {
        if (!d.getStatus().isEditable()) {
            throw new BusinessException("DEP_STATE", "Penyusutan yang sudah diajukan tidak bisa dihitung ulang");
        }
        YearMonth ym = YearMonth.parse(d.getPeriod(), AssetService.YYYYMM);
        Long dup = jdbc.queryForObject("""
                SELECT count(*) FROM fin.depreciation_run WHERE plant_id = ? AND period = ? AND id <> ? AND status <> 'CANCELLED'""",
                Long.class, d.getPlantId(), d.getPeriod(), d.getId());
        if (dup != null && dup > 0) {
            throw new BusinessException("DEP_DUP", "Penyusutan periode " + d.getPeriod() + " sudah ada");
        }
        jdbc.update("DELETE FROM fin.depreciation_line WHERE run_id = ?", d.getId());
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal fiscal = BigDecimal.ZERO;
        short no = 1;
        for (AssetService.Line l : service.compute(d.getPlantId(), ym)) {
            jdbc.update("INSERT INTO fin.depreciation_line (run_id, line_no, asset_id, amount, fiscal_amount, book_value_after) VALUES (?, ?, ?, ?, ?, ?)",
                    d.getId(), no++, l.asset().getId(), l.amount(), l.fiscal(), l.asset().bookValue().subtract(l.amount()));
            total = total.add(l.amount());
            fiscal = fiscal.add(l.fiscal());
        }
        d.setTotalAmount(total);
        d.setTotalFiscal(fiscal);
        d.setAssetCount(no - 1);
    }

    @Override
    public void validateSubmit(DepreciationRun d) {
        if (d.getAssetCount() == 0) {
            throw new BusinessException("DEP_EMPTY", "Tidak ada aset yang disusutkan; klik Hitung dulu");
        }
    }

    @Override
    public void onPost(DepreciationRun d) {
        List<JournalPostingService.IdLine> lines = new ArrayList<>();
        Map<String, BigDecimal> credit = new LinkedHashMap<>();
        String desc = "Penyusutan " + d.getPeriod();
        jdbc.query("""
                SELECT l.asset_id, l.amount, l.fiscal_amount, c.expense_account_id, c.accum_account_id, a.cost_center_id
                FROM fin.depreciation_line l JOIN fin.fixed_asset a ON a.id = l.asset_id JOIN fin.asset_category c ON c.id = a.category_id
                WHERE l.run_id = ?""", rs -> {
            BigDecimal amt = rs.getBigDecimal("amount");
            lines.add(new JournalPostingService.IdLine(rs.getLong("expense_account_id"), (Long) rs.getObject("cost_center_id"), desc, amt, null));
            credit.merge(String.valueOf(rs.getLong("accum_account_id")), amt, BigDecimal::add);
            FixedAsset a = assets.findById(rs.getLong("asset_id")).orElseThrow();
            a.setAccumulated(a.getAccumulated().add(amt));
            a.setFiscalAccumulated(a.getFiscalAccumulated().add(rs.getBigDecimal("fiscal_amount")));
            if (a.getAccumulated().compareTo(a.getAcquisitionCost().subtract(a.getResidualValue())) >= 0) {
                a.setAssetState("FULLY_DEP");
            }
            assets.save(a);
        }, d.getId());
        credit.forEach((acc, amt) -> lines.add(new JournalPostingService.IdLine(Long.valueOf(acc), null, desc, null, amt)));
        journals.postIds(d.getPlantId(), d.getDocDate(), lines, "DEP", d.getId(), d.getDocNo(), desc);
    }

    @Override
    public DepreciationRun reverse(DepreciationRun d, LocalDate date, String reason) {
        journals.reverseFor("DEP", d.getId(), date, reason);
        jdbc.query("SELECT asset_id, amount, fiscal_amount FROM fin.depreciation_line WHERE run_id = ?", rs -> {
            FixedAsset a = assets.findById(rs.getLong(1)).orElseThrow();
            a.setAccumulated(a.getAccumulated().subtract(rs.getBigDecimal(2)));
            a.setFiscalAccumulated(a.getFiscalAccumulated().subtract(rs.getBigDecimal(3)));
            a.setAssetState("ACTIVE");
            assets.save(a);
        }, d.getId());
        return d;
    }
}

@RestController
@RequestMapping("/api/fin/depreciations")
class DepreciationController extends DocumentApi<DepreciationRun> {

    private final DepreciationHandler dep;
    private final JdbcTemplate jdbc;

    DepreciationController(DepreciationHandler handler, DepreciationRunRepository repo, Support support, JdbcTemplate jdbc) {
        super(handler, repo, support, DepreciationRun.class);
        this.dep = handler;
        this.jdbc = jdbc;
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("totalAmount", "totalFiscal", "assetCount");
    }

    @Override
    protected void beforeSave(DepreciationRun d, boolean isNew) {
        try {
            YearMonth.parse(d.getPeriod(), AssetService.YYYYMM);
        } catch (RuntimeException e) {
            throw new BusinessException("PERIOD", "Periode harus berformat YYYYMM");
        }
        if (isNew) {
            d.setDocDate(YearMonth.parse(d.getPeriod(), AssetService.YYYYMM).atEndOfMonth().isAfter(LocalDate.now())
                    ? LocalDate.now() : YearMonth.parse(d.getPeriod(), AssetService.YYYYMM).atEndOfMonth());
        }
    }

    @Override
    protected void enrich(Map<String, Object> body, DepreciationRun d) {
        body.put("lines", jdbc.queryForList("""
                SELECT l.line_no, a.doc_no, a.name, l.amount, l.fiscal_amount, l.book_value_after FROM fin.depreciation_line l
                JOIN fin.fixed_asset a ON a.id = l.asset_id WHERE l.run_id = ? ORDER BY l.line_no""", d.getId()));
    }

    @PostMapping("/{id}/calculate")
    @Transactional
    public Envelope calculate(@PathVariable Long id) {
        DepreciationRun d = dep.load(id);
        workflow().assertEditable(dep, d);
        dep.calculate(d);
        dep.save(d);
        return envelope(d);
    }
}

@RestController
@RequestMapping("/api/fin/asset-categories")
class AssetCategoryController extends MasterController<AssetCategory> {
    AssetCategoryController(AssetCategoryRepository r, PermissionService p) { super(r, p, AssetCategory.class); }
    @Override protected String menuCode() { return "FIN-40"; }
    @Override protected String[] immutableFields() { return new String[]{"code"}; }
    @Override protected Sort defaultSort() { return Sort.by("code"); }
}
