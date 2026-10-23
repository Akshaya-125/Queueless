package com.queueless.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ServiceRequest(
        @NotBlank(message = "Service name is required") @Size(max = 100, message = "Name is too long") String name,
        @Size(max = 150, message = "Location is too long") String location,
        @Size(max = 255, message = "Description is too long") String description,
        Boolean active) {
}
