package id.herbatech.erp.scm;

import id.herbatech.erp.shared.audit.AuditContext;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.esign.ESignatureService;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perubahan status lot Quarantine → Released/Rejected/Hold. Satu-satunya tombol ini hanya ada di QMS
 * dan hanya untuk peran QA Release Officer (PRD QMS aturan 1), dengan tanda tangan elektronik.
 */
@Service
public class LotStatusService {

    /** Peristiwa untuk modul lain (MRP, costing, retur supplier). */
    public record LotStatusChanged(Long lotId, String lotNo, Long itemId, String from, String to, String reason) {
    }

    private final LotRepository lots;
    private final PermissionService perm;
    private final ESignatureService esign;
    private final ApplicationEventPublisher events;

    public LotStatusService(LotRepository lots, PermissionService perm, ESignatureService esign, ApplicationEventPublisher events) {
        this.lots = lots;
        this.perm = perm;
        this.esign = esign;
        this.events = events;
    }

    @Transactional
    public Lot change(Long lotId, Lot.QcStatus to, String reason, String password) {
        perm.require("QMS-09", Action.RELEASE);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("REASON_REQUIRED", "Alasan perubahan status lot wajib diisi");
        }
        Lot lot = lots.findById(lotId).orElseThrow(() -> new NotFoundException("Lot", lotId));
        Lot.QcStatus from = lot.getQcStatus();
        if (from == to) {
            throw new BusinessException("LOT_STATUS", "Lot sudah berstatus " + to);
        }
        if (from == Lot.QcStatus.REJECTED) {
            throw new BusinessException("LOT_STATUS", "Lot yang sudah Rejected tidak bisa diubah statusnya");
        }
        esign.sign("LOT", lotId, ESignatureService.Meaning.RELEASE, password, to + ": " + reason);
        AuditContext.withReason(reason, () -> {
            lot.setQcStatus(to);
            return lots.saveAndFlush(lot);
        });
        events.publishEvent(new LotStatusChanged(lot.getId(), lot.getLotNo(), lot.getItemId(), from.name(), to.name(), reason));
        return lot;
    }
}
