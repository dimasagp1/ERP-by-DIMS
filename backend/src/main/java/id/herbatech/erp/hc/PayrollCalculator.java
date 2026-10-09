package id.herbatech.erp.hc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Perhitungan gaji satu karyawan untuk satu periode — fungsi murni tanpa akses database.
 * <ul>
 *   <li>Upah lembur: upah sejam = 1/173 × upah sebulan (gaji pokok + tunjangan tetap). Hari kerja: jam pertama 1,5×,
 *       berikutnya 2×. Hari istirahat/libur (5 hari kerja): 8 jam pertama 2×, jam ke-9 3×, jam ke-10–11 4× (PP 35/2021).</li>
 *   <li>BPJS Kesehatan & JP memakai batas upah; JHT, JKK, JKM tanpa batas.</li>
 *   <li>PPh 21 Januari–November: TER bulanan × penghasilan bruto (termasuk premi JKK, JKM, BPJS Kesehatan
 *       yang dibayar perusahaan). Desember: PPh setahun (Pasal 17) dikurangi PPh yang sudah dipotong.</li>
 * </ul>
 */
final class PayrollCalculator {

    private PayrollCalculator() {
    }

    record Params(BigDecimal kesEr, BigDecimal kesEe, BigDecimal kesCap, BigDecimal jhtEr, BigDecimal jhtEe,
                  BigDecimal jpEr, BigDecimal jpEe, BigDecimal jpCap, BigDecimal jkk, BigDecimal jkm, BigDecimal otDivisor,
                  BigDecimal jobExpenseRate, BigDecimal jobExpenseMax, boolean deductAbsence) {
    }

    record ComponentDef(String code, String name, String kind, boolean fixed, boolean taxable, String account, int seq) {
    }

    record Overtime(String dayType, BigDecimal hours) {
    }

    /** Akumulasi Januari s.d. bulan sebelumnya pada tahun pajak yang sama. */
    record Ytd(BigDecimal taxableGross, BigDecimal pph21, BigDecimal pensionContributions) {
        static final Ytd ZERO = new Ytd(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    record TerBracket(BigDecimal upperLimit, BigDecimal rate) {
    }

    record Input(Map<String, BigDecimal> salary, int workDays, int payableDays, int absentDays, List<Overtime> overtime,
                 BigDecimal loanInstallment, String terCategory, BigDecimal ptkp, boolean december, Ytd ytd) {
    }

    record Line(String code, String name, String kind, String account, BigDecimal quantity, BigDecimal amount, int seq) {
    }

    record Result(List<Line> lines, BigDecimal gross, BigDecimal taxableGross, BigDecimal deductions, BigDecimal net,
                  BigDecimal employerCost, BigDecimal pph21, BigDecimal terRate, BigDecimal overtimeHours) {
    }

    /** Tabel TER kategori tertentu belum diisi. */
    static final class MissingTerTable extends RuntimeException {
        MissingTerTable(String category) {
            super("Tabel TER PPh 21 kategori " + category + " belum diisi (HC-99)");
        }
    }

    private static final BigDecimal[][] PASAL_17 = {
            {new BigDecimal("60000000"), new BigDecimal("0.05")},
            {new BigDecimal("250000000"), new BigDecimal("0.15")},
            {new BigDecimal("500000000"), new BigDecimal("0.25")},
            {new BigDecimal("5000000000"), new BigDecimal("0.30")},
            {null, new BigDecimal("0.35")},
    };

    static Result calculate(Input in, Params p, Map<String, ComponentDef> defs, List<TerBracket> ter) {
        List<Line> lines = new ArrayList<>();
        BigDecimal factor = in.workDays() <= 0 ? BigDecimal.ONE
                : BigDecimal.valueOf(Math.min(in.payableDays(), in.workDays())).divide(BigDecimal.valueOf(in.workDays()), 10, RoundingMode.HALF_UP);

        BigDecimal fixedFull = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal taxable = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : in.salary().entrySet()) {
            ComponentDef def = defs.get(e.getKey());
            if (def == null || !"EARNING".equals(def.kind()) || e.getValue().signum() == 0) {
                continue;
            }
            if (def.fixed()) {
                fixedFull = fixedFull.add(e.getValue());
            }
            BigDecimal amt = rp(e.getValue().multiply(factor));
            lines.add(line(def, null, amt));
            gross = gross.add(amt);
            if (def.taxable()) {
                taxable = taxable.add(amt);
            }
        }
        BigDecimal wage = rp(fixedFull.multiply(factor));

        // Lembur
        BigDecimal hourly = fixedFull.divide(p.otDivisor(), 10, RoundingMode.HALF_UP);
        BigDecimal otHours = BigDecimal.ZERO;
        BigDecimal otMultiplied = BigDecimal.ZERO;
        for (Overtime o : in.overtime()) {
            otHours = otHours.add(o.hours());
            otMultiplied = otMultiplied.add(multiplied(o.dayType(), o.hours()));
        }
        if (otHours.signum() > 0) {
            BigDecimal ot = rp(hourly.multiply(otMultiplied));
            lines.add(line(def(defs, "LEMBUR"), otHours, ot));
            gross = gross.add(ot);
            taxable = taxable.add(ot);
        }

        // Potongan hari tidak hadir (ALPA / izin tidak dibayar)
        if (p.deductAbsence() && in.absentDays() > 0 && in.workDays() > 0) {
            BigDecimal pot = rp(fixedFull.divide(BigDecimal.valueOf(in.workDays()), 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(in.absentDays()))).negate();
            lines.add(line(def(defs, "POT_ALPA"), BigDecimal.valueOf(in.absentDays()), pot));
            gross = gross.add(pot);
            taxable = taxable.add(pot);
        }

        // BPJS
        BigDecimal kesBase = wage.min(p.kesCap());
        BigDecimal jpBase = wage.min(p.jpCap());
        BigDecimal kesP = rp(kesBase.multiply(p.kesEr()));
        BigDecimal jhtP = rp(wage.multiply(p.jhtEr()));
        BigDecimal jpP = rp(jpBase.multiply(p.jpEr()));
        BigDecimal jkk = rp(wage.multiply(p.jkk()));
        BigDecimal jkm = rp(wage.multiply(p.jkm()));
        BigDecimal kesE = rp(kesBase.multiply(p.kesEe()));
        BigDecimal jhtE = rp(wage.multiply(p.jhtEe()));
        BigDecimal jpE = rp(jpBase.multiply(p.jpEe()));
        BigDecimal employer = BigDecimal.ZERO;
        for (Object[] c : new Object[][]{{"BPJSKES_P", kesP}, {"JHT_P", jhtP}, {"JP_P", jpP}, {"JKK", jkk}, {"JKM", jkm}}) {
            BigDecimal amt = (BigDecimal) c[1];
            ComponentDef d = def(defs, (String) c[0]);
            if (amt.signum() != 0) {
                lines.add(line(d, null, amt));
                employer = employer.add(amt);
                if (d.taxable()) {
                    taxable = taxable.add(amt);
                }
            }
        }

        // PPh 21
        BigDecimal pension = jhtE.add(jpE);
        BigDecimal terRate = BigDecimal.ZERO;
        BigDecimal pph;
        if (in.december()) {
            pph = annualTax(in, p, taxable, pension);
        } else {
            terRate = terRate(in.terCategory(), taxable, ter);
            pph = taxable.multiply(terRate).setScale(0, RoundingMode.FLOOR);
        }

        BigDecimal loan = in.loanInstallment() == null ? BigDecimal.ZERO : in.loanInstallment();
        BigDecimal deductions = BigDecimal.ZERO;
        for (Object[] c : new Object[][]{{"BPJSKES_E", kesE}, {"JHT_E", jhtE}, {"JP_E", jpE}, {"PPH21", pph}, {"PINJAMAN", loan}}) {
            BigDecimal amt = (BigDecimal) c[1];
            if (amt.signum() != 0) {
                lines.add(line(def(defs, (String) c[0]), null, amt));
                deductions = deductions.add(amt);
            }
        }
        lines.sort(Comparator.comparingInt(Line::seq));
        return new Result(lines, gross, taxable, deductions, gross.subtract(deductions), employer, pph, terRate, otHours);
    }

    /** Jam lembur × pengali sesuai jenis hari. */
    static BigDecimal multiplied(String dayType, BigDecimal hours) {
        BigDecimal h = hours;
        if ("WORKDAY".equals(dayType)) {
            BigDecimal first = h.min(BigDecimal.ONE);
            return first.multiply(new BigDecimal("1.5")).add(h.subtract(first).max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(2)));
        }
        BigDecimal eight = BigDecimal.valueOf(8);
        BigDecimal a = h.min(eight);
        BigDecimal b = h.subtract(eight).max(BigDecimal.ZERO).min(BigDecimal.ONE);
        BigDecimal c = h.subtract(BigDecimal.valueOf(9)).max(BigDecimal.ZERO).min(BigDecimal.valueOf(2));
        return a.multiply(BigDecimal.valueOf(2)).add(b.multiply(BigDecimal.valueOf(3))).add(c.multiply(BigDecimal.valueOf(4)));
    }

    static BigDecimal terRate(String category, BigDecimal gross, List<TerBracket> table) {
        if (table == null || table.isEmpty()) {
            throw new MissingTerTable(category);
        }
        List<TerBracket> sorted = new ArrayList<>(table);
        sorted.sort(Comparator.comparing(TerBracket::upperLimit, Comparator.nullsLast(Comparator.naturalOrder())));
        for (TerBracket b : sorted) {
            if (b.upperLimit() == null || gross.compareTo(b.upperLimit()) <= 0) {
                return b.rate();
            }
        }
        throw new MissingTerTable(category);
    }

    /** PPh 21 Desember: PPh setahun (Pasal 17) − PPh Januari–November. Bisa negatif (lebih potong dikembalikan). */
    static BigDecimal annualTax(Input in, Params p, BigDecimal taxableThisMonth, BigDecimal pensionThisMonth) {
        BigDecimal bruto = in.ytd().taxableGross().add(taxableThisMonth);
        BigDecimal jobExpense = bruto.multiply(p.jobExpenseRate()).min(p.jobExpenseMax());
        BigDecimal neto = bruto.subtract(jobExpense).subtract(in.ytd().pensionContributions()).subtract(pensionThisMonth);
        BigDecimal pkp = neto.subtract(in.ptkp());
        pkp = pkp.signum() <= 0 ? BigDecimal.ZERO
                : pkp.divide(BigDecimal.valueOf(1000), 0, RoundingMode.FLOOR).multiply(BigDecimal.valueOf(1000));
        return pasal17(pkp).subtract(in.ytd().pph21());
    }

    static BigDecimal pasal17(BigDecimal pkp) {
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal lower = BigDecimal.ZERO;
        for (BigDecimal[] b : PASAL_17) {
            BigDecimal upper = b[0];
            if (pkp.compareTo(lower) <= 0) {
                break;
            }
            BigDecimal portion = (upper == null ? pkp : pkp.min(upper)).subtract(lower);
            tax = tax.add(portion.multiply(b[1]));
            if (upper == null) {
                break;
            }
            lower = upper;
        }
        return tax.setScale(0, RoundingMode.FLOOR);
    }

    private static Line line(ComponentDef d, BigDecimal qty, BigDecimal amount) {
        return new Line(d.code(), d.name(), d.kind(), d.account(), qty, amount, d.seq());
    }

    private static ComponentDef def(Map<String, ComponentDef> defs, String code) {
        ComponentDef d = defs.get(code);
        if (d == null) {
            throw new IllegalStateException("Komponen gaji sistem " + code + " belum ada di master HC-99");
        }
        return d;
    }

    private static BigDecimal rp(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP);
    }
}
