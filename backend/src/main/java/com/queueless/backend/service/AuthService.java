package com.queueless.backend.service;

import com.queueless.backend.dto.AuthResponse;
import com.queueless.backend.dto.LoginRequest;
import com.queueless.backend.dto.RegisterRequest;
import com.queueless.backend.dto.UserDto;
import com.queueless.backend.entity.Role;
import com.queueless.backend.entity.User;
import com.queueless.backend.exception.ApiException;
import com.queueless.backend.repository.UserRepository;
import com.queueless.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    /** Public registration always creates a normal USER. Admins are created by the seeder only. */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User saved = users.save(new User(req.fullName().trim(), email, encoder.encode(req.password()), Role.USER));
        return new AuthResponse(jwt.generate(saved), UserDto.of(saved));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email().trim().toLowerCase())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        return new AuthResponse(jwt.generate(user), UserDto.of(user));
    }
}
