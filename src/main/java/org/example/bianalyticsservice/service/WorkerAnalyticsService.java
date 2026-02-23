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

        List<JobDto> jobsWithAggregatedWorkers = aggregateWorkersForDisplay(filteredJobs);

        // Calculate speed index contribution percentages for all workers
        List<JobDto> jobsWithSpeedIndexContributions = calculateSpeedIndexContributions(
                jobsWithAggregatedWorkers,
                benchmarks
        );

        return WorkerAnalyticsResponseDto.builder()
                .jobs(jobsWithSpeedIndexContributions)
                .workerStats(workerStats)
                .benchmarks(benchmarks)
                .allWorkerIds(allWorkerIds)
                .allProductIds(allProductIds)
                .cappedDays(cappedDays)
                .build();
    }

    /**
     * Calculates speed index contribution percentages for all workers across all jobs.
     * For each worker on each job, calculates what percentage of their speed index numerator
     * came from that specific job. All jobs for a worker sum to 100%.
     *
     * @param jobs Jobs with aggregated workers
     * @param benchmarks Product benchmarks (hours per unit)
     * @return Jobs with speedIndexContributionPercentage populated in each WorkerTimeDto
     */
    private List<JobDto> calculateSpeedIndexContributions(
            List<JobDto> jobs,
            Map<String, BigDecimal> benchmarks
    ) {
        // Step 1: For each worker, calculate their total numerator across ALL jobs
        // Numerator = sum of (benchmark × worker_contribution) for all jobs
        Map<String, BigDecimal> workerTotalNumerators = new HashMap<>();

        for (JobDto job : jobs) {
            BigDecimal benchmark = benchmarks.get(job.getProductTypeId());
            if (benchmark == null) continue;

            // Calculate total hours on this job (all workers combined)
            BigDecimal totalJobHours = job.getWorkers().stream()
                    .map(WorkerTimeDto::getMinutesWorked)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(60), 4, BigDecimal.ROUND_HALF_UP);

            if (totalJobHours.compareTo(BigDecimal.ZERO) == 0) continue;

            // For each worker on this job, add their contribution to their total numerator
            for (WorkerTimeDto worker : job.getWorkers()) {
                String workerKey = worker.getWorkerId() + "|" +
                        (worker.getResourceId() != null ? worker.getResourceId() : worker.getWorkerId());

                BigDecimal workerHours = worker.getMinutesWorked()
                        .divide(BigDecimal.valueOf(60), 4, BigDecimal.ROUND_HALF_UP);

                // Worker's contribution to this job = their portion of total work
                BigDecimal workerContribution = workerHours.divide(totalJobHours, 4, BigDecimal.ROUND_HALF_UP);

                // This job's contribution to worker's numerator
                BigDecimal jobNumeratorContribution = benchmark.multiply(workerContribution);

                workerTotalNumerators.merge(workerKey, jobNumeratorContribution, BigDecimal::add);
            }
        }

        // Step 2: Calculate percentage contribution for each worker on each job
        return jobs.stream()
                .map(job -> {
                    BigDecimal benchmark = benchmarks.get(job.getProductTypeId());
                    if (benchmark == null) return job;

                    BigDecimal totalJobHours = job.getWorkers().stream()
                            .map(WorkerTimeDto::getMinutesWorked)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(60), 4, BigDecimal.ROUND_HALF_UP);

                    if (totalJobHours.compareTo(BigDecimal.ZERO) == 0) return job;

                    List<WorkerTimeDto> workersWithContributions = job.getWorkers().stream()
                            .map(worker -> {
                                String workerKey = worker.getWorkerId() + "|" +
                                        (worker.getResourceId() != null ? worker.getResourceId() : worker.getWorkerId());

                                BigDecimal totalNumerator = workerTotalNumerators.get(workerKey);
                                if (totalNumerator == null || totalNumerator.compareTo(BigDecimal.ZERO) == 0) {
                                    return worker;
                                }

                                BigDecimal workerHours = worker.getMinutesWorked()
                                        .divide(BigDecimal.valueOf(60), 4, BigDecimal.ROUND_HALF_UP);

                                BigDecimal workerContribution = workerHours.divide(totalJobHours, 4, BigDecimal.ROUND_HALF_UP);
                                BigDecimal jobNumeratorContribution = benchmark.multiply(workerContribution);

                                // Percentage of worker's total numerator that came from this job
                                BigDecimal contributionPercentage = jobNumeratorContribution
                                        .divide(totalNumerator, 4, BigDecimal.ROUND_HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                        .setScale(2, BigDecimal.ROUND_HALF_UP);

                                worker.setSpeedIndexContributionPercentage(contributionPercentage);
                                return worker;
                            })
                            .collect(Collectors.toList());

                    job.setWorkers(workersWithContributions);
                    return job;
                })
                .collect(Collectors.toList());
    }

    /**
     * Aggregates workers by workerId+resourceId, summing their minutes across different dates.
     * This is needed for job-level display where we don't want to show the same worker multiple times
     * if they worked on the same job across multiple dates.
     */
    private List<JobDto> aggregateWorkersForDisplay(List<JobDto> jobs) {
        return jobs.stream()
                .map(job -> {
                    // Group workers by workerId+resourceId composite key
                    Map<String, WorkerTimeDto> aggregatedWorkers = new LinkedHashMap<>();

                    for (WorkerTimeDto worker : job.getWorkers()) {
                        String key = worker.getWorkerId() + "|" +
                                    (worker.getResourceId() != null ? worker.getResourceId() : worker.getWorkerId());

                        if (aggregatedWorkers.containsKey(key)) {
                            // Worker already exists, sum the minutes
                            WorkerTimeDto existing = aggregatedWorkers.get(key);
                            existing.setMinutesWorked(
                                existing.getMinutesWorked().add(worker.getMinutesWorked())
                            );
                        } else {
                            // New worker, add to map (workDate is set to null since we're aggregating across dates)
                            aggregatedWorkers.put(key, WorkerTimeDto.builder()
                                    .workerId(worker.getWorkerId())
                                    .resourceId(worker.getResourceId())
                                    .workDate(null) // Aggregated across dates, so no single date
                                    .minutesWorked(worker.getMinutesWorked())
                                    .build());
                        }
                    }

                    // Create new JobDto with aggregated workers
                    return JobDto.builder()
                            .id(job.getId())
                            .numerZlecenia(job.getNumerZlecenia())
                            .date(job.getDate())
                            .productTypeId(job.getProductTypeId())
                            .quantity(job.getQuantity())
                            .workers(new ArrayList<>(aggregatedWorkers.values()))
                            .totalMinutes(job.getTotalMinutes())
                            .rwElements(job.getRwElements())
                            .rwSuma(job.getRwSuma())
                            .pwElements(job.getPwElements())
                            .pwSuma(job.getPwSuma())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
