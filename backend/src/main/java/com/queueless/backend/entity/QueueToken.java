package com.queueless.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One token for one service on one day. Token numbers restart at 1 every day;
 * old rows are kept as history. (service, day, number) is unique in the database.
 */
@Entity
@Table(name = "queue_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_token_service_day_number",
                columnNames = {"service_id", "service_date", "token_number"}),
        indexes = {
                @Index(name = "idx_token_queue", columnList = "service_id,service_date,status"),
                @Index(name = "idx_token_user", columnList = "user_id")
        })
public class QueueToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceCounter service;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "token_number", nullable = false)
    private int tokenNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TokenStatus status = TokenStatus.WAITING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant calledAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;

    protected QueueToken() { }

    public QueueToken(ServiceCounter service, User user, LocalDate serviceDate, int tokenNumber) {
        this.service = service;
        this.user = user;
        this.serviceDate = serviceDate;
        this.tokenNumber = tokenNumber;
    }

    /** Sets the new status and the matching timestamp. Validation happens in the service layer. */
    public void applyStatus(TokenStatus next, Instant now) {
        this.status = next;
        switch (next) {
            case CALLED -> calledAt = now;
            case SERVING -> startedAt = now;
            case COMPLETED -> completedAt = now;
            case CANCELLED -> cancelledAt = now;
            default -> { }
        }
    }

    public Long getId() { return id; }
    public ServiceCounter getService() { return service; }
    public User getUser() { return user; }
    public LocalDate getServiceDate() { return serviceDate; }
    public int getTokenNumber() { return tokenNumber; }
    public TokenStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCalledAt() { return calledAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
