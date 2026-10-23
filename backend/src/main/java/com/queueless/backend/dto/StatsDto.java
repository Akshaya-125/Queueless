package com.queueless.backend.dto;

/** Status counts are for today; totalTokens is all-time. averageServiceMinutes is null until a token is completed. */
public record StatsDto(long totalTokens, long todayTokens, long waiting, long called, long serving,
                       long completed, long cancelled, Double averageServiceMinutes) {
}
