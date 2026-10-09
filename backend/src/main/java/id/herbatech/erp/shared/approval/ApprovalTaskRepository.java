package id.herbatech.erp.shared.approval;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, Long> {

    List<ApprovalTask> findByDocTypeAndDocIdOrderByLevelAscIdAsc(String docType, Long docId);

    List<ApprovalTask> findByStatus(ApprovalTask.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ApprovalTask t where t.id = :id")
    Optional<ApprovalTask> findForUpdate(Long id);
}
