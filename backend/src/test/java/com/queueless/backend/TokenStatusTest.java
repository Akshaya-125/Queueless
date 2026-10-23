package com.queueless.backend;

import com.queueless.backend.entity.TokenStatus;
import org.junit.jupiter.api.Test;
import static com.queueless.backend.entity.TokenStatus.*;
import static org.junit.jupiter.api.Assertions.*;

/** Unit test: pure logic, no Spring, no database. */
class TokenStatusTest {

    @Test
    void validTransitionsAreAllowed() {
        assertTrue(WAITING.canTransitionTo(CALLED));
        assertTrue(WAITING.canTransitionTo(CANCELLED));
        assertTrue(CALLED.canTransitionTo(SERVING));
        assertTrue(CALLED.canTransitionTo(CANCELLED));
        assertTrue(SERVING.canTransitionTo(COMPLETED));
    }

    @Test
    void invalidTransitionsAreRejected() {
        assertFalse(WAITING.canTransitionTo(SERVING));
        assertFalse(WAITING.canTransitionTo(COMPLETED));
        assertFalse(CALLED.canTransitionTo(COMPLETED));
        assertFalse(SERVING.canTransitionTo(CANCELLED));
        assertFalse(SERVING.canTransitionTo(WAITING));
    }

    @Test
    void finalStatesCannotChange() {
        for (TokenStatus next : TokenStatus.values()) {
            assertFalse(COMPLETED.canTransitionTo(next));
            assertFalse(CANCELLED.canTransitionTo(next));
        }
    }

    @Test
    void activeStatusesExcludeFinalOnes() {
        assertTrue(TokenStatus.ACTIVE.contains(WAITING));
        assertFalse(TokenStatus.ACTIVE.contains(COMPLETED));
        assertFalse(TokenStatus.ACTIVE.contains(CANCELLED));
    }
}
