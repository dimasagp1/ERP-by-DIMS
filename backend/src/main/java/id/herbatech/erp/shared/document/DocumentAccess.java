package id.herbatech.erp.shared.document;

import id.herbatech.erp.shared.approval.ApprovalService;
import id.herbatech.erp.shared.approval.OrgDirectory;
import id.herbatech.erp.shared.domain.DocumentEntity;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.CurrentUser;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import id.herbatech.erp.shared.security.ViewScope;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/**
 * Siapa boleh melihat satu dokumen (PRD §2 kolom "Lihat"):
 * pemegang hak lihat menu sesuai cakupan datanya, pembuat/pengaju dokumen, dan approver yang terlibat.
 */
@Component
public class DocumentAccess {

    private final PermissionService perm;
    private final OrgDirectory org;
    private final ApprovalService approvals;

    public DocumentAccess(PermissionService perm, OrgDirectory org, ApprovalService approvals) {
        this.perm = perm;
        this.org = org;
        this.approvals = approvals;
    }

    /** Cakupan efektif: dokumen layanan mandiri lintas departemen dilihat penuh oleh staf aplikasi pemiliknya. */
    public ViewScope scope(DocType type) {
        ViewScope s = perm.viewScope(type.getAppCode());
        return type.getEssMenuCode() != null && s != ViewScope.OWN && perm.has(type.getMenuCode(), Action.VIEW) ? ViewScope.ALL : s;
    }

    /** Id pembuat yang boleh dilihat di daftar menu; null = tanpa batas. */
    public Set<Long> visibleCreators(DocType type) {
        return org.visibleCreatorIds(UserContext.userId(), scope(type));
    }

    public boolean canView(DocType type, DocumentEntity doc, Long requesterId) {
        CurrentUser me = UserContext.current();
        if (Objects.equals(me.id(), doc.getCreatedBy()) || Objects.equals(me.id(), requesterId)) {
            return true;
        }
        if (perm.has(type.getMenuCode(), Action.VIEW)) {
            Set<Long> visible = visibleCreators(type);
            if (visible == null || visible.contains(doc.getCreatedBy()) || doc.getCreatedBy() == null) {
                return true;
            }
        }
        return approvals.involves(type.getCode(), doc.getId(), me);
    }

    public void requireView(DocType type, DocumentEntity doc, Long requesterId) {
        if (!canView(type, doc, requesterId)) {
            throw new AccessDeniedException("Dokumen " + doc.getDocNo() + " di luar cakupan data Anda");
        }
    }
}
