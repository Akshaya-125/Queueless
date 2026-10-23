package com.queueless.backend.dto;

import com.queueless.backend.entity.ServiceCounter;

public record ServiceDto(Long id, String name, String location, String description, boolean active,
                         long waitingCount, Double averageServiceMinutes) {
    public static ServiceDto of(ServiceCounter s, long waiting, Double avgMinutes) {
        return new ServiceDto(s.getId(), s.getName(), s.getLocation(), s.getDescription(), s.isActive(), waiting, avgMinutes);
    }
}
