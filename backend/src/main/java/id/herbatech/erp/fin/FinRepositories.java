package id.herbatech.erp.fin;

import id.herbatech.erp.shared.web.MasterRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

interface AccountRepository extends MasterRepository<Account> {
    Optional<Account> findByCode(String code);
}

interface AccountMappingRepository extends MasterRepository<AccountMapping> {
    Optional<AccountMapping> findByTxnTypeAndActiveTrue(String txnType);
}

interface JournalEntryRepository extends JpaRepository<JournalEntry, Long>, JpaSpecificationExecutor<JournalEntry> {

    @EntityGraph(attributePaths = "lines")
    Optional<JournalEntry> findWithLinesById(Long id);

    List<JournalEntry> findBySourceDocTypeAndSourceDocIdOrderById(String sourceDocType, Long sourceDocId);
}
