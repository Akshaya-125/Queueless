package com.queueless.backend.repository;

import com.queueless.backend.entity.QueueToken;
import com.queueless.backend.entity.TokenStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QueueTokenRepository extends JpaRepository<QueueToken, Long> {

    @Query("select coalesce(max(t.tokenNumber), 0) from QueueToken t where t.service.id = :serviceId and t.serviceDate = :date")
    Integer findMaxTokenNumber(@Param("serviceId") Long serviceId, @Param("date") LocalDate date);

    /** Scalar query: does not load the token into the persistence context. */
    @Query("select t.service.id from QueueToken t where t.id = :id")
    Optional<Long> findServiceIdById(@Param("id") Long id);

    boolean existsByUserIdAndServiceIdAndServiceDateAndStatusIn(Long userId, Long serviceId, LocalDate date, Collection<TokenStatus> statuses);

    long countByServiceIdAndServiceDateAndStatus(Long serviceId, LocalDate date, TokenStatus status);

    long countByServiceIdAndServiceDateAndStatusAndTokenNumberLessThan(Long serviceId, LocalDate date, TokenStatus status, int tokenNumber);

    long countByServiceDate(LocalDate date);

    long countByServiceDateAndStatus(LocalDate date, TokenStatus status);

    Optional<QueueToken> findFirstByServiceIdAndServiceDateAndStatusOrderByTokenNumberAsc(Long serviceId, LocalDate date, TokenStatus status);

    @EntityGraph(attributePaths = {"service", "user"})
    List<QueueToken> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"service", "user"})
    Optional<QueueToken> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"service", "user"})
    List<QueueToken> findAllByOrderByIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"service", "user"})
    List<QueueToken> findByServiceIdOrderByIdDesc(Long serviceId, Pageable pageable);

    @EntityGraph(attributePaths = {"service", "user"})
    List<QueueToken> findByStatusOrderByIdDesc(TokenStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"service", "user"})
    List<QueueToken> findByServiceIdAndStatusOrderByIdDesc(Long serviceId, TokenStatus status, Pageable pageable);

    List<QueueToken> findByServiceIdAndStatusOrderByCompletedAtDesc(Long serviceId, TokenStatus status, Pageable pageable);

    List<QueueToken> findByStatusOrderByCompletedAtDesc(TokenStatus status, Pageable pageable);
}
