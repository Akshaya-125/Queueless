package com.queueless.backend.repository;

import com.queueless.backend.entity.ServiceCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ServiceCounterRepository extends JpaRepository<ServiceCounter, Long> {

    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    List<ServiceCounter> findByActiveTrueOrderByNameAsc();
    List<ServiceCounter> findAllByOrderByNameAsc();

    /** SELECT ... FOR UPDATE. Used as a per-service mutex for every queue change. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ServiceCounter s where s.id = :id")
    Optional<ServiceCounter> findByIdForUpdate(@Param("id") Long id);
}
