package com.queueless.backend.controller;

import com.queueless.backend.dto.ServiceDto;
import com.queueless.backend.service.ServiceCounterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceCounterService services;

    public ServiceController(ServiceCounterService services) {
        this.services = services;
    }

    @GetMapping
    public List<ServiceDto> list() {
        return services.listActive();
    }
}
