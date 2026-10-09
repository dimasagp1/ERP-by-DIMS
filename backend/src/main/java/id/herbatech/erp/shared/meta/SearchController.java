package id.herbatech.erp.shared.meta;

import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.CurrentUser;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pencarian global Ctrl+K (PRD §15.2): menu, nomor dokumen, item, lot. */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    public record Hit(String kind, String code, String title, String subtitle, String menuCode, String docType, Long id) {
    }

    private final MenuRegistry registry;
    private final PermissionService perm;
    private final JdbcTemplate jdbc;

    public SearchController(MenuRegistry registry, PermissionService perm, JdbcTemplate jdbc) {
        this.registry = registry;
        this.perm = perm;
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Hit> search(@RequestParam String q) {
        CurrentUser me = UserContext.current();
        String term = q.trim();
        if (term.isEmpty()) {
            return List.of();
        }
        String lower = term.toLowerCase(Locale.ROOT);
        List<Hit> hits = new ArrayList<>();

        registry.allMenus().values().stream()
                .filter(m -> m.code().toLowerCase(Locale.ROOT).contains(lower) || m.name().toLowerCase(Locale.ROOT).contains(lower))
                .filter(m -> perm.has(me, m.code(), Action.VIEW))
                .limit(8)
                .forEach(m -> hits.add(new Hit("MENU", m.code(), m.name(), registry.groupOf(m.code()), m.code(), null, null)));

        jdbc.query("""
                        SELECT doc_type, doc_id, doc_no, menu_code, summary, status FROM core.document_index
                        WHERE plant_id = ? AND (lower(doc_no) LIKE ? OR summary ILIKE ?)
                        ORDER BY updated_at DESC LIMIT 30""",
                rs -> {
                    if (hits.stream().filter(h -> "DOC".equals(h.kind())).count() < 10
                            && perm.has(me, rs.getString("menu_code"), Action.VIEW)) {
                        hits.add(new Hit("DOC", rs.getString("doc_no"), rs.getString("summary"), rs.getString("status"),
                                rs.getString("menu_code"), rs.getString("doc_type"), rs.getLong("doc_id")));
                    }
                }, me.plantId(), "%" + lower + "%", "%" + term + "%");

        if (perm.has(me, "SYS-06", Action.VIEW)) {
            jdbc.query("SELECT id, code, name, type FROM sys.item WHERE code ILIKE ? OR name ILIKE ? ORDER BY code LIMIT 5",
                    rs -> {
                        hits.add(new Hit("ITEM", rs.getString("code"), rs.getString("name"), rs.getString("type"), "SYS-06", null,
                                rs.getLong("id")));
                    }, "%" + term + "%", "%" + term + "%");
        }
        if (perm.has(me, "SCM-45", Action.VIEW)) {
            jdbc.query("""
                            SELECT l.id, l.lot_no, i.name, l.qc_status FROM scm.lot l JOIN sys.item i ON i.id = l.item_id
                            WHERE l.lot_no ILIKE ? ORDER BY l.created_at DESC LIMIT 5""",
                    rs -> {
                        hits.add(new Hit("LOT", rs.getString("lot_no"), rs.getString("name"), rs.getString("qc_status"),
                                "SCM-45", null, rs.getLong("id")));
                    }, "%" + term + "%");
        }
        return hits;
    }
}
