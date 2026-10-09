package id.herbatech.erp.scm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface LotRepository extends JpaRepository<Lot, Long> {

    Optional<Lot> findByItemIdAndLotNo(Long itemId, String lotNo);
}
