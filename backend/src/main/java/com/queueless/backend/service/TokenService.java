package com.queueless.backend.service;

import com.queueless.backend.dto.TokenDto;
import com.queueless.backend.entity.QueueToken;
import com.queueless.backend.entity.ServiceCounter;
import com.queueless.backend.entity.TokenStatus;
import com.queueless.backend.exception.ApiException;
import com.queueless.backend.repository.QueueTokenRepository;
import com.queueless.backend.repository.ServiceCounterRepository;
import com.queueless.backend.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Queue rules (documented in README):
 *  - Every change to a service's queue (book, call next, serving, complete, cancel) first takes a
 *    database row lock on that service (SELECT ... FOR UPDATE). Changes to the same queue are therefore
 *    serialised: two clients can never get the same token number and two admins can never call the same token.
 *  - Queue order = ascending token number within (service, day). Token numbers restart at 1 each day.
 *  - Queue position = 1 + number of WAITING tokens with a smaller number, same service and day.
 *  - Status changes are validated against TokenStatus.canTransitionTo.
 */
@Service
public class TokenService {

    private final ServiceCounterRepository counters;
    private final QueueTokenRepository tokens;
    private final UserRepository users;
    private final QueueMetrics metrics;
    private final Clock clock;

    public TokenService(ServiceCounterRepository counters, QueueTokenRepository tokens, UserRepository users,
                        QueueMetrics metrics, Clock clock) {
        this.counters = counters;
        this.tokens = tokens;
        this.users = users;
        this.metrics = metrics;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ user operations

    @Transactional
    public TokenDto book(Long userId, Long serviceId) {
        ServiceCounter service = counters.findByIdForUpdate(serviceId)
                .orElseThrow(() -> ApiException.notFound("Service not found"));
        if (!service.isActive()) {
            throw ApiException.conflict("This service is not accepting tokens right now");
        }
        LocalDate today = LocalDate.now(clock);
        if (tokens.existsByUserIdAndServiceIdAndServiceDateAndStatusIn(userId, serviceId, today, TokenStatus.ACTIVE)) {
            throw ApiException.conflict("You already have an active token for this service");
        }
        int next = tokens.findMaxTokenNumber(serviceId, today) + 1;
        QueueToken token = tokens.save(new QueueToken(service, users.getReferenceById(userId), today, next));
        return toUserDto(token);
    }

    @Transactional(readOnly = true)
    public List<TokenDto> myTokens(Long userId) {
        return tokens.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, 100)).stream().map(this::toUserDto).toList();
    }

    @Transactional(readOnly = true)
    public TokenDto myToken(Long userId, Long tokenId) {
        QueueToken t = tokens.findWithDetailsById(tokenId)
                .filter(x -> x.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Token not found"));
        return toUserDto(t);
    }

    @Transactional
    public TokenDto cancelMine(Long userId, Long tokenId) {
        QueueToken t = lockAndLoad(tokenId);
        if (!t.getUser().getId().equals(userId)) {
            throw ApiException.notFound("Token not found");
        }
        if (t.getStatus() != TokenStatus.WAITING) {
            throw ApiException.conflict("Only tokens that are still waiting can be cancelled");
        }
        t.applyStatus(TokenStatus.CANCELLED, Instant.now(clock));
        return toUserDto(t);
    }

    // ------------------------------------------------------------------ administrator operations

    @Transactional
    public TokenDto callNext(Long serviceId) {
        counters.findByIdForUpdate(serviceId).orElseThrow(() -> ApiException.notFound("Service not found"));
        LocalDate today = LocalDate.now(clock);
        QueueToken next = tokens.findFirstByServiceIdAndServiceDateAndStatusOrderByTokenNumberAsc(serviceId, today, TokenStatus.WAITING)
                .orElseThrow(() -> ApiException.notFound("No tokens are waiting in this queue"));
        next.applyStatus(TokenStatus.CALLED, Instant.now(clock));
        return TokenDto.of(next, null, null, true);
    }

    @Transactional
    public TokenDto markServing(Long tokenId) {
        return transition(tokenId, TokenStatus.SERVING);
    }

    @Transactional
    public TokenDto complete(Long tokenId) {
        return transition(tokenId, TokenStatus.COMPLETED);
    }

    @Transactional
    public TokenDto cancelAsAdmin(Long tokenId) {
        return transition(tokenId, TokenStatus.CANCELLED);
    }

    @Transactional(readOnly = true)
    public List<TokenDto> adminList(Long serviceId, TokenStatus status) {
        PageRequest page = PageRequest.of(0, 200);
        List<QueueToken> list;
        if (serviceId != null && status != null) {
            list = tokens.findByServiceIdAndStatusOrderByIdDesc(serviceId, status, page);
        } else if (serviceId != null) {
            list = tokens.findByServiceIdOrderByIdDesc(serviceId, page);
        } else if (status != null) {
            list = tokens.findByStatusOrderByIdDesc(status, page);
        } else {
            list = tokens.findAllByOrderByIdDesc(page);
        }
        return list.stream().map(t -> TokenDto.of(t, null, null, true)).toList();
    }

    // ------------------------------------------------------------------ helpers

    private TokenDto transition(Long tokenId, TokenStatus target) {
        QueueToken t = lockAndLoad(tokenId);
        if (!t.getStatus().canTransitionTo(target)) {
            throw ApiException.conflict("A token that is " + t.getStatus() + " cannot be changed to " + target);
        }
        t.applyStatus(target, Instant.now(clock));
        return TokenDto.of(t, null, null, true);
    }

    /** Lock the token's service first, then read the token, so we never act on stale data. */
    private QueueToken lockAndLoad(Long tokenId) {
        Long serviceId = tokens.findServiceIdById(tokenId).orElseThrow(() -> ApiException.notFound("Token not found"));
        counters.findByIdForUpdate(serviceId).orElseThrow(() -> ApiException.notFound("Service not found"));
        return tokens.findById(tokenId).orElseThrow(() -> ApiException.notFound("Token not found"));
    }

    private TokenDto toUserDto(QueueToken t) {
        Integer position = null;
        Integer eta = null;
        if (t.getStatus() == TokenStatus.WAITING) {
            long ahead = tokens.countByServiceIdAndServiceDateAndStatusAndTokenNumberLessThan(
                    t.getService().getId(), t.getServiceDate(), TokenStatus.WAITING, t.getTokenNumber());
            position = (int) ahead + 1;
            eta = metrics.estimateMinutes(t.getService().getId(), ahead);
        }
        return TokenDto.of(t, position, eta, false);
    }
}
