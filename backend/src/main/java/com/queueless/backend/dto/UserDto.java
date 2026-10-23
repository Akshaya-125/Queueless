package com.queueless.backend.dto;

import com.queueless.backend.entity.Role;
import com.queueless.backend.entity.User;

public record UserDto(Long id, String fullName, String email, Role role) {
    public static UserDto of(User u) {
        return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getRole());
    }
}
