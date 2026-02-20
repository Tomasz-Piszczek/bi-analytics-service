package org.example.bianalyticsservice.service;

import lombok.RequiredArgsConstructor;
import org.example.bianalyticsservice.controller.analytics.dto.*;
import org.example.bianalyticsservice.controller.employee.dto.EmployeeHoursDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkerAnalyticsService {

    private final WorkerAnalyticsCacheService workerAnalyticsCacheService;
    private final WorkerStatsCalculator workerStatsCalculator;

    public WorkerAnalyticsResponseDto getWorkerAnalytics(WorkerAnalyticsRequestDto request) {
        // Get already-mapped jobs from cache (DB + JSON parsing cached)
        List<JobDto> allJobs = workerAnalyticsCacheService.getAllJobs();

        // Get all employee hours from cache (single DB query, cached)
        Map<String, EmployeeHoursDto> allEmployeeHoursMap = workerAnalyticsCacheService.getAllEmployeeHoursMap();

        Set<String> allWorkerIds = allJobs.stream()
                .flatMap(job -> job.getWorkers().stream())
                .map(WorkerTimeDto::getWorkerId)
                .collect(Collectors.toSet());

        Set<String> allProductIds = allJobs.stream()
                .map(JobDto::getProductTypeId)
                .collect(Collectors.toSet());

        List<JobDto> filteredJobs = workerStatsCalculator.filterJobs(
                allJobs,
                request.getDateFrom(),
                request.getDateTo(),
                request.getSelectedProducts(),
                request.getExcludedWorkers(),
                Boolean.TRUE.equals(request.getSoloOnly())
        );

        Set<String> filteredWorkerIds = filteredJobs.stream()
                .flatMap(job -> job.getWorkers().stream())
                .map(WorkerTimeDto::getWorkerId)
                .collect(Collectors.toSet());

        // Filter employee hours to only include filtered workers (from cached data)
        List<EmployeeHoursDto> employeeHours = filteredWorkerIds.stream()
                .map(allEmployeeHoursMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, Map<LocalDate, BigDecimal>> attendance = workerStatsCalculator.buildAttendanceMap(employeeHours);
        Map<String, BigDecimal> benchmarks = workerStatsCalculator.calculateBenchmarks(filteredJobs);

        // Extract composite IDs from excludedWorkers (those containing "|")
        Set<String> excludedCompositeIds = request.getExcludedWorkers() != null
                ? request.getExcludedWorkers().stream()
                        .filter(id -> id.contains("|"))
                        .collect(Collectors.toSet())
                : null;

        List<WorkerStatsDto> workerStats = workerStatsCalculator.calculateAllWorkerStats(
                filteredJobs, allJobs, benchmarks, attendance,
                Boolean.TRUE.equals(request.getIgnoreInternalWork()),
                excludedCompositeIds
        );

        List<CappedDayDto> cappedDays = workerStatsCalculator.collectAllCappedDays(workerStats);

        return WorkerAnalyticsResponseDto.builder()
                .jobs(filteredJobs)
                .workerStats(workerStats)
                .benchmarks(benchmarks)
                .allWorkerIds(allWorkerIds)
                .allProductIds(allProductIds)
                .cappedDays(cappedDays)
                .build();
    }
}
