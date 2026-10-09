package id.herbatech.erp.shared.document;

import java.util.List;

/**
 * Modul yang membuat dokumen turunan (mis. FIN membuat jurnal otomatis dari GR) mendaftarkan diri di sini,
 * sehingga panel "Dokumen terkait" di form bisa menautkan lintas departemen tanpa kernel mengenal modulnya.
 */
public interface RelatedDocumentProvider {

    record RelatedDoc(String docType, Long docId, String docNo, String menuCode, String status, String relation) {
    }

    List<RelatedDoc> related(String docType, Long docId);
}
