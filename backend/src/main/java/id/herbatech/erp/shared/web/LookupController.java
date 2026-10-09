package id.herbatech.erp.shared.web;

import id.herbatech.erp.shared.error.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pilihan untuk dropdown/autocomplete di form: {@code GET /api/lookup/items?q=kun&type=RM}.
 * Sumber lookup didaftarkan modul lewat {@link LookupSource}; hanya kolom yang terdaftar yang bisa difilter.
 */
@RestController
@RequestMapping("/api/lookup")
public class LookupController {

    public record Option(Long id, String code, String name, String extra) {
    }

    private final JdbcTemplate jdbc;
    private final Map<String, LookupSource.Def> defs = new HashMap<>();

    public LookupController(JdbcTemplate jdbc, List<LookupSource> sources) {
        this.jdbc = jdbc;
        sources.forEach(s -> defs.putAll(s.lookups()));
    }

    @GetMapping("/{name}")
    public List<Option> lookup(@PathVariable String name, @RequestParam Map<String, String> params) {
        LookupSource.Def def = defs.get(name);
        if (def == null) {
            throw new BusinessException("LOOKUP", "Lookup tidak dikenal: " + name);
        }
        StringBuilder sql = new StringBuilder("SELECT id, " + def.codeCol() + " AS code, " + def.nameCol() + " AS name, "
                + (def.extraCol() == null ? "NULL" : def.extraCol()) + " AS extra FROM " + def.table() + " WHERE 1=1");
        List<Object> args = new ArrayList<>();
        String ids = params.get("ids");
        if (ids != null && !ids.isBlank()) {
            List<Long> idList = Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf).toList();
            if (idList.isEmpty()) {
                return List.of();
            }
            sql.append(" AND id IN (").append(String.join(",", idList.stream().map(i -> "?").toList())).append(")");
            args.addAll(idList);
        } else {
            if (def.baseWhere() != null && !"true".equals(params.get("all"))) {
                sql.append(" AND ").append(def.baseWhere());
            }
            String q = params.get("q");
            if (q != null && !q.isBlank()) {
                sql.append(" AND (").append(def.codeCol()).append(" ILIKE ? OR ").append(def.nameCol()).append(" ILIKE ?)");
                args.add("%" + q.trim() + "%");
                args.add("%" + q.trim() + "%");
            }
            for (String f : def.filterCols()) {
                String v = params.get(f);
                if (v != null && !v.isBlank()) {
                    List<String> vals = Arrays.stream(v.split(",")).map(String::trim).toList();
                    sql.append(" AND ").append(f).append("::text IN (")
                            .append(String.join(",", vals.stream().map(x -> "?").toList())).append(")");
                    args.addAll(vals);
                }
            }
        }
        sql.append(" ORDER BY ").append(def.codeCol()).append(" LIMIT ?");
        args.add(Math.min(parseInt(params.get("limit"), 50), 500));
        return jdbc.query(sql.toString(), (rs, i) -> new Option(rs.getLong("id"), rs.getString("code"),
                rs.getString("name"), rs.getString("extra")), args.toArray());
    }

    private static int parseInt(String s, int def) {
        try {
            return s == null ? def : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
