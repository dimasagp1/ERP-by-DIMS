package id.herbatech.erp.shared.meta;

import id.herbatech.erp.shared.document.DocTypeRepository;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.CurrentUser;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/** Aplikasi & menu yang boleh dilihat pengguna (launcher dan navbar). */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final MenuRegistry registry;
    private final PermissionService perm;
    private final DocTypeRepository docTypes;

    public MetaController(MenuRegistry registry, PermissionService perm, DocTypeRepository docTypes) {
        this.registry = registry;
        this.perm = perm;
        this.docTypes = docTypes;
    }

    public record DocTypeRef(String code, String name, String appCode, String menuCode, String essMenuCode, boolean requiresEsign) {
    }

    /** Jenis dokumen → menu, untuk tautan notifikasi dan dokumen terkait. */
    @GetMapping("/doc-types")
    public List<DocTypeRef> docTypes() {
        UserContext.current();
        return docTypes.findAll().stream()
                .map(d -> new DocTypeRef(d.getCode(), d.getName(), d.getAppCode(), d.getMenuCode(), d.getEssMenuCode(), d.isRequiresEsign()))
                .toList();
    }

    public record AppView(String code, String name, String shortName, String color, boolean self, boolean canSettings,
                          List<String> masters, List<MenuRegistry.DocDef> docs, java.util.Map<String, String> sends,
                          List<MenuRegistry.MenuGroup> groups) {
    }

    @GetMapping("/apps")
    public List<AppView> apps() {
        CurrentUser me = UserContext.current();
        Set<String> visible = perm.visibleApps(me);
        return registry.apps().stream()
                .filter(a -> visible.contains(a.code()))
                .map(a -> new AppView(a.code(), a.name(), a.shortName(), a.color(), a.self(),
                        perm.canManageSettings(a.code()), a.masters(), a.docs(), a.sends(),
                        a.groups().stream()
                                .map(g -> new MenuRegistry.MenuGroup(g.name(),
                                        g.items().stream().filter(i -> perm.has(me, i.code(), Action.VIEW)).toList()))
                                .filter(g -> !g.items().isEmpty())
                                .toList()))
                .toList();
    }

    /** Semua aplikasi (termasuk yang tidak bisa diakses) untuk peta hubungan departemen di dashboard. */
    @GetMapping("/apps/all")
    public List<MenuRegistry.AppDef> allApps() {
        UserContext.current();
        return registry.apps();
    }
}
