package com.queueless.backend.service;

import com.queueless.backend.dto.StatsDto;
import com.queueless.backend.entity.QueueToken;
import com.queueless.backend.entity.TokenStatus;
import com.queueless.backend.repository.QueueTokenRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalDouble;

@Service
public class StatsService {

    private final QueueTokenRepository tokens;
    private final Clock clock;

    public StatsService(QueueTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StatsDto stats() {
        LocalDate today = LocalDate.now(clock);
        List<QueueToken> completed = tokens.findByStatusOrderByCompletedAtDesc(TokenStatus.COMPLETED, PageRequest.of(0, 1000));
        OptionalDouble avg = QueueMetrics.averageMinutes(completed);
        return new StatsDto(tokens.count(), tokens.countByServiceDate(today),
                tokens.countByServiceDateAndStatus(today, TokenStatus.WAITING),
                tokens.countByServiceDateAndStatus(today, TokenStatus.CALLED),
                tokens.countByServiceDateAndStatus(today, TokenStatus.SERVING),
                tokens.countByServiceDateAndStatus(today, TokenStatus.COMPLETED),
                tokens.countByServiceDateAndStatus(today, TokenStatus.CANCELLED),
                avg.isPresent() ? avg.getAsDouble() : null);
    }
}
