package com.queueless.backend.dto;

import jakarta.validation.constraints.NotNull;

public record BookTokenRequest(@NotNull(message = "Please choose a service") Long serviceId) {
}
