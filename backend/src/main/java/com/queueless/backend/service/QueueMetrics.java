package com.queueless.backend.service;

import com.queueless.backend.entity.QueueToken;
import com.queueless.backend.entity.TokenStatus;
import com.queueless.backend.repository.QueueTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Waiting-time estimate = (waiting tokens ahead of you) x (average service time).
 * Average service time = mean of (completed - started) over the last 50 completed tokens of that service,
 * or a default when nothing has been completed yet. At least 1 minute per person is assumed.
 * This is only an estimate.
 */
@Component
public class QueueMetrics {

    private final QueueTokenRepository tokens;
    private final double defaultMinutes;

    public QueueMetrics(QueueTokenRepository tokens, @Value("${queueless.default-service-minutes:5}") double defaultMinutes) {
        this.tokens = tokens;
        this.defaultMinutes = defaultMinutes;
    }

    public static OptionalDouble averageMinutes(List<QueueToken> completed) {
        return completed.stream()
                .filter(t -> t.getStartedAt() != null && t.getCompletedAt() != null)
                .mapToDouble(t -> Duration.between(t.getStartedAt(), t.getCompletedAt()).toMillis() / 60000.0)
                .average();
    }

    /** Average for one service, or null if nothing has been completed yet. */
    public Double measuredAverage(Long serviceId) {
        List<QueueToken> done = tokens.findByServiceIdAndStatusOrderByCompletedAtDesc(serviceId, TokenStatus.COMPLETED, PageRequest.of(0, 50));
        OptionalDouble avg = averageMinutes(done);
        return avg.isPresent() ? avg.getAsDouble() : null;
    }

    public int estimateMinutes(Long serviceId, long peopleAhead) {
        Double measured = measuredAverage(serviceId);
        double perPerson = Math.max(1.0, measured != null ? measured : defaultMinutes);
        return (int) Math.ceil(peopleAhead * perPerson);
    }
}
