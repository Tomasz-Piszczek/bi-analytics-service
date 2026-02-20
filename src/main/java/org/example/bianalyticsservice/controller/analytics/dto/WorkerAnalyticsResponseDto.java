package org.example.bianalyticsservice.controller.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerAnalyticsResponseDto {
    private List<JobDto> jobs;
    private List<WorkerStatsDto> workerStats;
    private Map<String, BigDecimal> benchmarks;
    private Set<String> allWorkerIds;
    private Set<String> allProductIds;
    private List<CappedDayDto> cappedDays;
}