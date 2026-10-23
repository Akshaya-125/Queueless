package com.queueless.backend.dto;

public record AuthResponse(String token, UserDto user) {
}
