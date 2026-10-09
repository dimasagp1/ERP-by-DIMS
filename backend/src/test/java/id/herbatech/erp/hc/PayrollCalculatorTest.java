package id.herbatech.erp.hc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Angka harapan dihitung manual dari parameter seed V010. */
class PayrollCalculatorTest {

    static final PayrollCalculator.Params P = new PayrollCalculator.Params(new BigDecimal("0.04"), new BigDecimal("0.01"),
            new BigDecimal("12000000"), new BigDecimal("0.037"), new BigDecimal("0.02"), new BigDecimal("0.02"),
            new BigDecimal("0.01"), new BigDecimal("10547400"), new BigDecimal("0.0024"), new BigDecimal("0.003"),
            new BigDecimal("173"), new BigDecimal("0.05"), new BigDecimal("6000000"), true);

    static final Map<String, PayrollCalculator.ComponentDef> DEFS = new LinkedHashMap<>();

    static {
        Object[][] d = {
                {"GAPOK", "EARNING", true, true, "6101", 10}, {"TJAB", "EARNING", true, true, "6101", 20},
                {"TTRANS", "EARNING", false, true, "6101", 30}, {"TMAKAN", "EARNING", false, true, "6101", 40},
                {"LEMBUR", "EARNING", false, true, "6102", 50}, {"POT_ALPA", "EARNING", false, true, "6101", 60},
                {"BPJSKES_E", "DEDUCTION", false, false, "2107", 70}, {"JHT_E", "DEDUCTION", false, false, "2107", 71},
                {"JP_E", "DEDUCTION", false, false, "2107", 72}, {"PPH21", "DEDUCTION", false, false, "2104", 80},
                {"PINJAMAN", "DEDUCTION", false, false, "1240", 90}, {"BPJSKES_P", "EMPLOYER", false, true, "6103", 100},
                {"JHT_P", "EMPLOYER", false, false, "6103", 101}, {"JP_P", "EMPLOYER", false, false, "6103", 102},
                {"JKK", "EMPLOYER", false, true, "6103", 103}, {"JKM", "EMPLOYER", false, true, "6103", 104}};
        for (Object[] r : d) {
            DEFS.put((String) r[0], new PayrollCalculator.ComponentDef((String) r[0], (String) r[0], (String) r[1],
                    (Boolean) r[2], (Boolean) r[3], (String) r[4], (Integer) r[5]));
        }
    }

    static final List<PayrollCalculator.TerBracket> TER_A = List.of(
            new PayrollCalculator.TerBracket(new BigDecimal("5400000"), BigDecimal.ZERO),
            new PayrollCalculator.TerBracket(new BigDecimal("6750000"), new BigDecimal("0.01")),
            new PayrollCalculator.TerBracket(new BigDecimal("7500000"), new BigDecimal("0.0125")),
            new PayrollCalculator.TerBracket(null, new BigDecimal("0.34")));

    private static Map<String, BigDecimal> salary() {
        Map<String, BigDecimal> s = new LinkedHashMap<>();
        s.put("GAPOK", new BigDecimal("5600000"));
        s.put("TTRANS", new BigDecimal("500000"));
        s.put("TMAKAN", new BigDecimal("550000"));
        return s;
    }

    private static BigDecimal amount(PayrollCalculator.Result r, String code) {
        return r.lines().stream().filter(l -> l.code().equals(code)).findFirst().map(PayrollCalculator.Line::amount).orElse(BigDecimal.ZERO);
    }

    @Test
    void regularMonthWithTer() {
        var r = PayrollCalculator.calculate(new PayrollCalculator.Input(salary(), 21, 21, 0, List.of(), BigDecimal.ZERO, "A",
                new BigDecimal("54000000"), false, PayrollCalculator.Ytd.ZERO), P, DEFS, TER_A);
        assertThat(r.gross()).isEqualByComparingTo("6650000");
        assertThat(amount(r, "BPJSKES_P")).isEqualByComparingTo("224000");
        assertThat(amount(r, "JHT_P")).isEqualByComparingTo("207200");
        assertThat(amount(r, "JP_P")).isEqualByComparingTo("112000");
        assertThat(amount(r, "JKK")).isEqualByComparingTo("13440");
        assertThat(amount(r, "JKM")).isEqualByComparingTo("16800");
        // Bruto PPh = 6.650.000 + BPJS Kes perusahaan + JKK + JKM = 6.904.240 → TER A 1,25%
        assertThat(r.taxableGross()).isEqualByComparingTo("6904240");
        assertThat(r.terRate()).isEqualByComparingTo("0.0125");
        assertThat(r.pph21()).isEqualByComparingTo("86303");
        assertThat(r.deductions()).isEqualByComparingTo("310303");
        assertThat(r.net()).isEqualByComparingTo("6339697");
    }

    @Test
    void overtimeAndAbsenceAndProration() {
        var r = PayrollCalculator.calculate(new PayrollCalculator.Input(salary(), 20, 20, 2,
                List.of(new PayrollCalculator.Overtime("WORKDAY", new BigDecimal("3"))), BigDecimal.ZERO, "A",
                new BigDecimal("54000000"), false, PayrollCalculator.Ytd.ZERO), P, DEFS, TER_A);
        // Upah sejam 5.600.000/173; 3 jam hari kerja = 1,5 + 2 + 2 = 5,5 jam pengali
        assertThat(amount(r, "LEMBUR")).isEqualByComparingTo("178035");
        // Potongan 2 hari ALPA = 5.600.000 / 20 × 2
        assertThat(amount(r, "POT_ALPA")).isEqualByComparingTo("-560000");

        var half = PayrollCalculator.calculate(new PayrollCalculator.Input(salary(), 20, 10, 0, List.of(), BigDecimal.ZERO, "A",
                new BigDecimal("54000000"), false, PayrollCalculator.Ytd.ZERO), P, DEFS, TER_A);
        assertThat(amount(half, "GAPOK")).isEqualByComparingTo("2800000");
    }

    @Test
    void overtimeMultipliers() {
        assertThat(PayrollCalculator.multiplied("WORKDAY", new BigDecimal("1"))).isEqualByComparingTo("1.5");
        assertThat(PayrollCalculator.multiplied("WORKDAY", new BigDecimal("4"))).isEqualByComparingTo("7.5");
        assertThat(PayrollCalculator.multiplied("RESTDAY", new BigDecimal("8"))).isEqualByComparingTo("16");
        assertThat(PayrollCalculator.multiplied("RESTDAY", new BigDecimal("11"))).isEqualByComparingTo("27");
    }

    @Test
    void pasal17Progressive() {
        assertThat(PayrollCalculator.pasal17(new BigDecimal("60000000"))).isEqualByComparingTo("3000000");
        assertThat(PayrollCalculator.pasal17(new BigDecimal("100000000"))).isEqualByComparingTo("9000000");
        assertThat(PayrollCalculator.pasal17(new BigDecimal("300000000"))).isEqualByComparingTo("44000000");
        assertThat(PayrollCalculator.pasal17(BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void decemberUsesAnnualCalculation() {
        // Jan–Nov: bruto 11 × 6.904.240, PPh 11 × 86.303, iuran pensiun 11 × 168.000
        var ytd = new PayrollCalculator.Ytd(new BigDecimal("75946640"), new BigDecimal("949333"), new BigDecimal("1848000"));
        var r = PayrollCalculator.calculate(new PayrollCalculator.Input(salary(), 21, 21, 0, List.of(), BigDecimal.ZERO, "A",
                new BigDecimal("54000000"), true, ytd), P, DEFS, TER_A);
        // Bruto setahun 82.850.880; biaya jabatan 4.142.544; iuran 2.016.000; neto 76.692.336; PKP 22.692.000 → PPh 1.134.600
        assertThat(r.pph21()).isEqualByComparingTo(new BigDecimal("1134600").subtract(new BigDecimal("949333")));
    }

    @Test
    void missingTerTableIsRejected() {
        assertThatThrownBy(() -> PayrollCalculator.calculate(new PayrollCalculator.Input(salary(), 21, 21, 0, List.of(),
                BigDecimal.ZERO, "B", new BigDecimal("63000000"), false, PayrollCalculator.Ytd.ZERO), P, DEFS, null))
                .isInstanceOf(PayrollCalculator.MissingTerTable.class);
    }
}
