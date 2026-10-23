package com.queueless.backend.entity;

import java.util.List;

/**
 * Token lifecycle:
 * WAITING -> CALLED -> SERVING -> COMPLETED
 * WAITING -> CANCELLED, CALLED -> CANCELLED (e.g. no-show)
 * COMPLETED and CANCELLED are final.
 */
public enum TokenStatus {
    WAITING, CALLED, SERVING, COMPLETED, CANCELLED;

    public static final List<TokenStatus> ACTIVE = List.of(WAITING, CALLED, SERVING);

    public boolean canTransitionTo(TokenStatus next) {
        return switch (this) {
            case WAITING -> next == CALLED || next == CANCELLED;
            case CALLED -> next == SERVING || next == CANCELLED;
            case SERVING -> next == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
