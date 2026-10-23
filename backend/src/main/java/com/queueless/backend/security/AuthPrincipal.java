package com.queueless.backend.security;

import com.queueless.backend.entity.Role;

/** The logged-in user, taken from the verified JWT. */
public record AuthPrincipal(Long id, String email, Role role) {
}
