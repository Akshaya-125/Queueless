package com.queueless.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.queueless.backend.entity.QueueToken;
import com.queueless.backend.entity.TokenStatus;
import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenDto(Long id, Long serviceId, String serviceName, int tokenNumber, LocalDate serviceDate,
                       TokenStatus status, Integer queuePosition, Integer estimatedWaitMinutes,
                       Instant createdAt, Instant calledAt, Instant startedAt, Instant completedAt, Instant cancelledAt,
                       String customerName, String customerEmail) {

    public static TokenDto of(QueueToken t, Integer position, Integer eta, boolean includeCustomer) {
        return new TokenDto(t.getId(), t.getService().getId(), t.getService().getName(), t.getTokenNumber(),
                t.getServiceDate(), t.getStatus(), position, eta,
                t.getCreatedAt(), t.getCalledAt(), t.getStartedAt(), t.getCompletedAt(), t.getCancelledAt(),
                includeCustomer ? t.getUser().getFullName() : null,
                includeCustomer ? t.getUser().getEmail() : null);
    }
}
