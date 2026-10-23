package com.queueless.backend.controller;

import com.queueless.backend.dto.ServiceDto;
import com.queueless.backend.dto.ServiceRequest;
import com.queueless.backend.dto.StatsDto;
import com.queueless.backend.dto.TokenDto;
import com.queueless.backend.entity.TokenStatus;
import com.queueless.backend.service.ServiceCounterService;
import com.queueless.backend.service.StatsService;
import com.queueless.backend.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Every route here requires the ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ServiceCounterService services;
    private final TokenService tokens;
    private final StatsService stats;

    public AdminController(ServiceCounterService services, TokenService tokens, StatsService stats) {
        this.services = services;
        this.tokens = tokens;
        this.stats = stats;
    }

    @GetMapping("/services")
    public List<ServiceDto> listServices() {
        return services.listAll();
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceDto createService(@Valid @RequestBody ServiceRequest request) {
        return services.create(request);
    }

    @PutMapping("/services/{id}")
    public ServiceDto updateService(@PathVariable Long id, @Valid @RequestBody ServiceRequest request) {
        return services.update(id, request);
    }

    @GetMapping("/tokens")
    public List<TokenDto> listTokens(@RequestParam(required = false) Long serviceId,
                                     @RequestParam(required = false) TokenStatus status) {
        return tokens.adminList(serviceId, status);
    }

    @PostMapping("/services/{id}/call-next")
    public TokenDto callNext(@PathVariable Long id) {
        return tokens.callNext(id);
    }

    @PostMapping("/tokens/{id}/serving")
    public TokenDto serving(@PathVariable Long id) {
        return tokens.markServing(id);
    }

    @PostMapping("/tokens/{id}/complete")
    public TokenDto complete(@PathVariable Long id) {
        return tokens.complete(id);
    }

    @PostMapping("/tokens/{id}/cancel")
    public TokenDto cancel(@PathVariable Long id) {
        return tokens.cancelAsAdmin(id);
    }

    @GetMapping("/stats")
    public StatsDto stats() {
        return stats.stats();
    }
}
