package com.queueless.backend.controller;

import com.queueless.backend.dto.BookTokenRequest;
import com.queueless.backend.dto.TokenDto;
import com.queueless.backend.security.AuthPrincipal;
import com.queueless.backend.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    private final TokenService tokens;

    public TokenController(TokenService tokens) {
        this.tokens = tokens;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TokenDto book(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody BookTokenRequest request) {
        return tokens.book(me.id(), request.serviceId());
    }

    @GetMapping("/my")
    public List<TokenDto> mine(@AuthenticationPrincipal AuthPrincipal me) {
        return tokens.myTokens(me.id());
    }

    @GetMapping("/{id}")
    public TokenDto get(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id) {
        return tokens.myToken(me.id(), id);
    }

    @PostMapping("/{id}/cancel")
    public TokenDto cancel(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id) {
        return tokens.cancelMine(me.id(), id);
    }
}
