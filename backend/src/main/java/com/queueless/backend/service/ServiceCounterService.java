package com.queueless.backend.service;

import com.queueless.backend.dto.ServiceDto;
import com.queueless.backend.dto.ServiceRequest;
import com.queueless.backend.entity.ServiceCounter;
import com.queueless.backend.entity.TokenStatus;
import com.queueless.backend.exception.ApiException;
import com.queueless.backend.repository.QueueTokenRepository;
import com.queueless.backend.repository.ServiceCounterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class ServiceCounterService {

    private final ServiceCounterRepository counters;
    private final QueueTokenRepository tokens;
    private final QueueMetrics metrics;
    private final Clock clock;

    public ServiceCounterService(ServiceCounterRepository counters, QueueTokenRepository tokens, QueueMetrics metrics, Clock clock) {
        this.counters = counters;
        this.tokens = tokens;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ServiceDto> listActive() {
        return counters.findByActiveTrueOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceDto> listAll() {
        return counters.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public ServiceDto create(ServiceRequest req) {
        String name = req.name().trim();
        if (counters.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A service named '" + name + "' already exists");
        }
        ServiceCounter saved = counters.save(new ServiceCounter(name, clean(req.location()), clean(req.description()),
                req.active() == null || req.active()));
        return toDto(saved);
    }

    @Transactional
    public ServiceDto update(Long id, ServiceRequest req) {
        ServiceCounter s = counters.findById(id).orElseThrow(() -> ApiException.notFound("Service not found"));
        String name = req.name().trim();
        if (counters.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("A service named '" + name + "' already exists");
        }
        s.setName(name);
        s.setLocation(clean(req.location()));
        s.setDescription(clean(req.description()));
        if (req.active() != null) {
            s.setActive(req.active());
        }
        return toDto(s);
    }

    private ServiceDto toDto(ServiceCounter s) {
        LocalDate today = LocalDate.now(clock);
        long waiting = tokens.countByServiceIdAndServiceDateAndStatus(s.getId(), today, TokenStatus.WAITING);
        return ServiceDto.of(s, waiting, metrics.measuredAverage(s.getId()));
    }

    private static String clean(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
