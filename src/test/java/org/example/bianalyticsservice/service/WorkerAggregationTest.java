package org.example.bianalyticsservice.service;

import org.example.bianalyticsservice.controller.analytics.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for worker aggregation logic in WorkerAnalyticsService.
 *
 * This test class specifically addresses the bug where workers who worked on the same job
 * across multiple dates were being displayed multiple times in the UI, causing incorrect
 * hour calculations.
 *
 * Key issue: Backend SQL groups by workerId, resourceId, AND workDate, creating multiple
 * entries for the same worker on the same job across different dates. The aggregation
 * logic combines these into a single entry per worker per job.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Worker Aggregation Tests")
class WorkerAggregationTest {

    @Mock
    private WorkerAnalyticsCacheService workerAnalyticsCacheService;

    @Mock
    private WorkerStatsCalculator workerStatsCalculator;

    @InjectMocks
    private WorkerAnalyticsService workerAnalyticsService;

    private List<JobDto> testJobs;

    @BeforeEach
    void setUp() {
        testJobs = new ArrayList<>();
    }

    @Test
    @DisplayName("Should aggregate worker entries across multiple dates into single entry per worker")
    void shouldAggregateWorkerAcrossMultipleDates() {
        // Given: A job where TOMASZ PISZCZEK worked across 3 different dates
        JobDto job = JobDto.builder()
                .id(11018)
                .numerZlecenia("ZP/01962/2024")
                .date(LocalDate.of(2024, 10, 2))
                .productTypeId("KONTENER FIRANOWY")
                .quantity(BigDecimal.ONE)
                .totalMinutes(BigDecimal.valueOf(1347)) // 22.45 hours total
                .workers(Arrays.asList(
                        // TOMASZ PISZCZEK worked on 3 different dates
                        WorkerTimeDto.builder()
                                .workerId("TOMASZ PISZCZEK")
                                .resourceId("TOMASZ PISZCZEK")
                                .workDate(LocalDate.of(2024, 10, 4))
                                .minutesWorked(BigDecimal.valueOf(403)) // 6.72h
                                .build(),
                        WorkerTimeDto.builder()
                                .workerId("TOMASZ PISZCZEK")
                                .resourceId("TOMASZ PISZCZEK")
                                .workDate(LocalDate.of(2024, 10, 7))
                                .minutesWorked(BigDecimal.valueOf(21)) // 0.35h
                                .build(),
                        WorkerTimeDto.builder()
                                .workerId("TOMASZ PISZCZEK")
                                .resourceId("TOMASZ PISZCZEK")
                                .workDate(LocalDate.of(2024, 10, 10))
                                .minutesWorked(BigDecimal.valueOf(348)) // 5.80h
                                .build(),
                        // Paweł Górowski also worked on 3 different dates
                        WorkerTimeDto.builder()
                                .workerId("Paweł Górowski")
                                .resourceId("Paweł Górowski")
                                .workDate(LocalDate.of(2024, 10, 7))
                                .minutesWorked(BigDecimal.valueOf(23)) // 0.38h
                                .build(),
                        WorkerTimeDto.builder()
                                .workerId("Paweł Górowski")
                                .resourceId("Paweł Górowski")
                                .workDate(LocalDate.of(2024, 10, 9))
                                .minutesWorked(BigDecimal.valueOf(103)) // 1.72h
                                .build(),
                        WorkerTimeDto.builder()
                                .workerId("Paweł Górowski")
                                .resourceId("Paweł Górowski")
                                .workDate(LocalDate.of(2024, 10, 10))
                                .minutesWorked(BigDecimal.valueOf(342)) // 5.70h
                                .build()
                ))
                .build();

        testJobs.add(job);

        // Mock dependencies
        when(workerAnalyticsCacheService.getAllJobs()).thenReturn(testJobs);
        when(workerAnalyticsCacheService.getAllEmployeeHoursMap()).thenReturn(Collections.emptyMap());
        when(workerStatsCalculator.filterJobs(any(), any(), any(), any(), any(), any())).thenReturn(testJobs);
        when(workerStatsCalculator.buildAttendanceMap(any())).thenReturn(Collections.emptyMap());
        when(workerStatsCalculator.calculateBenchmarks(any())).thenReturn(Collections.emptyMap());
        when(workerStatsCalculator.calculateAllWorkerStats(any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(workerStatsCalculator.collectAllCappedDays(any())).thenReturn(Collections.emptyList());

        // When: Get worker analytics
        WorkerAnalyticsResponseDto response = workerAnalyticsService.getWorkerAnalytics(
                WorkerAnalyticsRequestDto.builder()
                        .dateFrom(LocalDate.of(2024, 10, 1))
                        .dateTo(LocalDate.of(2024, 10, 31))
                        .build()
        );

        // Then: Workers should be aggregated
        assertThat(response.getJobs()).hasSize(1);

        JobDto resultJob = response.getJobs().get(0);
        assertThat(resultJob.getWorkers()).hasSize(2); // Only 2 unique workers, not 6 entries

        // TOMASZ PISZCZEK should have total of 403 + 21 + 348 = 772 minutes (12.87 hours)
        WorkerTimeDto tomasz = resultJob.getWorkers().stream()
                .filter(w -> "TOMASZ PISZCZEK".equals(w.getWorkerId()))
                .findFirst()
                .orElseThrow();

        assertThat(tomasz.getMinutesWorked()).isEqualByComparingTo(BigDecimal.valueOf(772));
        assertThat(tomasz.getWorkDate()).isNull(); // Aggregated across dates, so no single date

        // Paweł Górowski should have total of 23 + 103 + 342 = 468 minutes (7.80 hours)
        WorkerTimeDto pawel = resultJob.getWorkers().stream()
                .filter(w -> "Paweł Górowski".equals(w.getWorkerId()))
                .findFirst()
                .orElseThrow();

        assertThat(pawel.getMinutesWorked()).isEqualByComparingTo(BigDecimal.valueOf(468));
        assertThat(pawel.getWorkDate()).isNull(); // Aggregated across dates, so no single date
    }

    @Test
    @DisplayName("Should not aggregate workers with different resourceIds")
    void shouldNotAggregateWorkersWithDifferentResources() {
        // Given: A worker used two different resources
        JobDto job = JobDto.builder()
                .id(1)
                .numerZlecenia("ZP/00001/2024")
                .date(LocalDate.of(2024, 10, 1))
                .productTypeId("TEST PRODUCT")
                .quantity(BigDecimal.ONE)
                .totalMinutes(BigDecimal.valueOf(300))
                .workers(Arrays.asList(
                        WorkerTimeDto.builder()
                                .workerId("Jan Kowalski")
                                .resourceId("Wycinanie") // Group resource
                                .workDate(LocalDate.of(2024, 10, 1))
                                .minutesWorked(BigDecimal.valueOf(150))
                                .build(),
                        WorkerTimeDto.builder()
                                .workerId("Jan Kowalski")
                                .resourceId("Jan Kowalski") // Personal resource
                                .workDate(LocalDate.of(2024, 10, 1))
                                .minutesWorked(BigDecimal.valueOf(150))
                                .build()
                ))
                .build();

        testJobs.add(job);

        // Mock dependencies
        when(workerAnalyticsCacheService.getAllJobs()).thenReturn(testJobs);
        when(workerAnalyticsCacheService.getAllEmployeeHoursMap()).thenReturn(Collections.emptyMap());
        when(workerStatsCalculator.filterJobs(any(), any(), any(), any(), any(), any())).thenReturn(testJobs);
        when(workerStatsCalculator.buildAttendanceMap(any())).thenReturn(Collections.emptyMap());
        when(workerStatsCalculator.calculateBenchmarks(any())).thenReturn(Collections.emptyMap());
        when(workerStatsCalculator.calculateAllWorkerStats(any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(workerStatsCalculator.collectAllCappedDays(any())).thenReturn(Collections.emptyList());

        // When: Get worker analytics
        WorkerAnalyticsResponseDto response = workerAnalyticsService.getWorkerAnalytics(
                WorkerAnalyticsRequestDto.builder()
                        .dateFrom(LocalDate.of(2024, 10, 1))
                        .dateTo(LocalDate.of(2024, 10, 31))
                        .build()
        );

        // Then: Should NOT aggregate because resourceIds are different
        assertThat(response.getJobs()).hasSize(1);
        JobDto resultJob = response.getJobs().get(0);
        assertThat(resultJob.getWorkers()).hasSize(2); // 2 entries because different resources

        // Each entry should have its original minutes
        assertThat(resultJob.getWorkers())
                .allMatch(w -> w.getMinutesWorked().compareTo(BigDecimal.valueOf(150)) == 0);
    }

    @Test
    @DisplayName("Should aggregate worker with same workerId and resourceId across dates")
    void shouldAggregateWorkerWithSameResourceAcrossDates() {
        // Given: A worker used the same resource across multiple dates
        JobDto job = JobDto.builder()
                .id(2)
                .numerZlecenia("ZP/00002/2024")
                .date(LocalDate.of(2024, 10, 1))
                .productTypeId("TEST PRODUCT")
                .quantity(BigDecimal.ONE)
                .totalMinutes(BigDecimal.valueOf(600))
                .workers(Arrays.asList(
                        WorkerTimeDto.builder()
                                .workerId("Jan Kowalski")
                                .resourceId("Wycinanie")
                                .workDate(LocalDate.of(2024, 10, 1))
                                .minutesWorked(BigDecimal.valueOf(200))
                                .build(),
                        WorkerTimeDto.builder()
                                .workerId("Jan Kowalski")
                                .resourceId("Wycinanie")
                                .workDate(LocalDate.of(2024, 10, 2))
                                .minutesWorked(BigDecimal.valueOf(400))
                                .build()
                ))
                .build();

        testJobs.add(job);

        // Mock dependencies
        when(workerAnalyticsCacheService.getAllJobs()).thenReturn(testJobs);
        when(workerAnalyticsCacheService.getAllEmployeeHoursMap()).thenReturn(Collections.emptyMap());
        when(workerStatsCalculator.filterJobs(any(), any(), any(), any(), any(), any())).thenReturn(testJobs);
        when(workerStatsCalculator.buildAttendanceMap(any())).thenReturn(Collections.emptyMap());
        when(workerStatsCalculator.calculateBenchmarks(any())).thenReturn(Collections.emptyMap());
        when(workerStatsCalculator.calculateAllWorkerStats(any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(workerStatsCalculator.collectAllCappedDays(any())).thenReturn(Collections.emptyList());

        // When: Get worker analytics
        WorkerAnalyticsResponseDto response = workerAnalyticsService.getWorkerAnalytics(
                WorkerAnalyticsRequestDto.builder()
                        .dateFrom(LocalDate.of(2024, 10, 1))
                        .dateTo(LocalDate.of(2024, 10, 31))
                        .build()
        );

        // Then: Should aggregate into single entry
        assertThat(response.getJobs()).hasSize(1);
        JobDto resultJob = response.getJobs().get(0);
        assertThat(resultJob.getWorkers()).hasSize(1); // Only 1 aggregated entry

        WorkerTimeDto worker = resultJob.getWorkers().get(0);
        assertThat(worker.getWorkerId()).isEqualTo("Jan Kowalski");
        assertThat(worker.getResourceId()).isEqualTo("Wycinanie");
        assertThat(worker.getMinutesWorked()).isEqualByComparingTo(BigDecimal.valueOf(600)); // 200 + 400
        assertThat(worker.getWorkDate()).isNull(); // Aggregated across dates
    }

    @Test
    @DisplayName("Should handle jobs with single worker entry (no aggregation needed)")
    void shouldHandleJobsWithSingleWorkerEntry() {
        // Given: A job with only one worker entry
        JobDto job = JobDto.builder()
                .id(3)
                .numerZlecenia("ZP/00003/2024")
                .date(LocalDate.of(2024, 10, 1))
                .productTypeId("TEST PRODUCT")
                .quantity(BigDecimal.ONE)
                .totalMinutes(BigDecimal.valueOf(120))
                .workers(Collections.singletonList(
                        WorkerTimeDto.builder()
                                .workerId("Jan Kowalski")
                                .resourceId("Jan Kowalski")
                                .workDate(LocalDate.of(2024, 10, 1))
                                .minutesWorked(BigDecimal.valueOf(120))
                                .build()
                ))
                .build();

        testJobs.add(job);

        // Mock dependencies
        when(workerAnalyticsCacheService.getAllJobs()).thenReturn(testJobs);
        when(workerAnalyticsCacheService.getAllEmployeeHoursMap()).thenReturn(Collections.emptyMap());
        when(workerStatsCalculator.filterJobs(any(), any(), any(), any(), any(), any())).thenReturn(testJobs);
        when(workerStatsCalculator.buildAttendanceMap(any())).thenReturn(Collections.emptyMap());
        when(workerStatsCalculator.calculateBenchmarks(any())).thenReturn(Collections.emptyMap());
        when(workerStatsCalculator.calculateAllWorkerStats(any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(workerStatsCalculator.collectAllCappedDays(any())).thenReturn(Collections.emptyList());

        // When: Get worker analytics
        WorkerAnalyticsResponseDto response = workerAnalyticsService.getWorkerAnalytics(
                WorkerAnalyticsRequestDto.builder()
                        .dateFrom(LocalDate.of(2024, 10, 1))
                        .dateTo(LocalDate.of(2024, 10, 31))
                        .build()
        );

        // Then: Should still have 1 worker entry
        assertThat(response.getJobs()).hasSize(1);
        JobDto resultJob = response.getJobs().get(0);
        assertThat(resultJob.getWorkers()).hasSize(1);

        WorkerTimeDto worker = resultJob.getWorkers().get(0);
        assertThat(worker.getMinutesWorked()).isEqualByComparingTo(BigDecimal.valueOf(120));
    }

    @Test
    @DisplayName("Should calculate exact speed index contribution percentages (Wpływ)")
    void shouldCalculateExactSpeedIndexContributions() {
        // Given: A worker who worked on 3 jobs with different benchmarks
        String workerId = "Jan Kowalski";
        String resourceId = "Jan Kowalski";

        /*
         * EXACT CALCULATION SCENARIO:
         *
         * Worker: Jan Kowalski (solo worker on all jobs for simplicity)
         *
         * Job 1: RAMA ALUMINIOWA
         * - Benchmark: 10h/unit
         * - Worker hours: 8h (480 minutes)
         * - Worker contribution: 8h / 8h = 1.0 (solo)
         * - Numerator contribution: 10 × 1.0 = 10
         *
         * Job 2: KONTENER FIRANOWY
         * - Benchmark: 20h/unit
         * - Worker hours: 16h (960 minutes)
         * - Worker contribution: 16h / 16h = 1.0 (solo)
         * - Numerator contribution: 20 × 1.0 = 20
         *
         * Job 3: DRZWI STALOWE
         * - Benchmark: 30h/unit
         * - Worker hours: 24h (1440 minutes)
         * - Worker contribution: 24h / 24h = 1.0 (solo)
         * - Numerator contribution: 30 × 1.0 = 30
         *
         * Total numerator: 10 + 20 + 30 = 60
         *
         * Speed Index Contributions (Wpływ):
         * - Job 1: (10 / 60) × 100 = 16.67%
         * - Job 2: (20 / 60) × 100 = 33.33%
         * - Job 3: (30 / 60) × 100 = 50.00%
         * - SUM: 16.67 + 33.33 + 50.00 = 100.00% ✅
         */

        List<JobDto> jobs = Arrays.asList(
                JobDto.builder()
                        .id(1)
                        .numerZlecenia("ZP/00001/2024")
                        .date(LocalDate.of(2024, 10, 1))
                        .productTypeId("RAMA ALUMINIOWA")
                        .quantity(BigDecimal.ONE)
                        .totalMinutes(BigDecimal.valueOf(480)) // 8h
                        .workers(Collections.singletonList(
                                WorkerTimeDto.builder()
                                        .workerId(workerId)
                                        .resourceId(resourceId)
                                        .minutesWorked(BigDecimal.valueOf(480))
                                        .build()
                        ))
                        .build(),
                JobDto.builder()
                        .id(2)
                        .numerZlecenia("ZP/00002/2024")
                        .date(LocalDate.of(2024, 10, 2))
                        .productTypeId("KONTENER FIRANOWY")
                        .quantity(BigDecimal.ONE)
                        .totalMinutes(BigDecimal.valueOf(960)) // 16h
                        .workers(Collections.singletonList(
                                WorkerTimeDto.builder()
                                        .workerId(workerId)
                                        .resourceId(resourceId)
                                        .minutesWorked(BigDecimal.valueOf(960))
                                        .build()
                        ))
                        .build(),
                JobDto.builder()
                        .id(3)
                        .numerZlecenia("ZP/00003/2024")
                        .date(LocalDate.of(2024, 10, 3))
                        .productTypeId("DRZWI STALOWE")
                        .quantity(BigDecimal.ONE)
                        .totalMinutes(BigDecimal.valueOf(1440)) // 24h
                        .workers(Collections.singletonList(
                                WorkerTimeDto.builder()
                                        .workerId(workerId)
                                        .resourceId(resourceId)
                                        .minutesWorked(BigDecimal.valueOf(1440))
                                        .build()
                        ))
                        .build()
        );

        testJobs = jobs;

        Map<String, BigDecimal> benchmarks = new HashMap<>();
        benchmarks.put("RAMA ALUMINIOWA", new BigDecimal("10"));
        benchmarks.put("KONTENER FIRANOWY", new BigDecimal("20"));
        benchmarks.put("DRZWI STALOWE", new BigDecimal("30"));

        // Mock dependencies
        when(workerAnalyticsCacheService.getAllJobs()).thenReturn(testJobs);
        when(workerAnalyticsCacheService.getAllEmployeeHoursMap()).thenReturn(Collections.emptyMap());
        when(workerStatsCalculator.filterJobs(any(), any(), any(), any(), any(), any())).thenReturn(testJobs);
        when(workerStatsCalculator.buildAttendanceMap(any())).thenReturn(Collections.emptyMap());
        when(workerStatsCalculator.calculateBenchmarks(any())).thenReturn(benchmarks);
        when(workerStatsCalculator.calculateAllWorkerStats(any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(workerStatsCalculator.collectAllCappedDays(any())).thenReturn(Collections.emptyList());

        // When: Get worker analytics
        WorkerAnalyticsResponseDto response = workerAnalyticsService.getWorkerAnalytics(
                WorkerAnalyticsRequestDto.builder()
                        .dateFrom(LocalDate.of(2024, 10, 1))
                        .dateTo(LocalDate.of(2024, 10, 31))
                        .build()
        );

        // Then: Verify exact speed index contribution percentages
        assertThat(response.getJobs()).hasSize(3);

        // Job 1: 16.67% contribution
        JobDto job1 = response.getJobs().stream()
                .filter(j -> "RAMA ALUMINIOWA".equals(j.getProductTypeId()))
                .findFirst()
                .orElseThrow();
        WorkerTimeDto worker1 = job1.getWorkers().get(0);
        assertThat(worker1.getSpeedIndexContributionPercentage())
                .as("RAMA ALUMINIOWA should contribute exactly 16.67% to speed index")
                .isEqualByComparingTo(new BigDecimal("16.67"));

        // Job 2: 33.33% contribution
        JobDto job2 = response.getJobs().stream()
                .filter(j -> "KONTENER FIRANOWY".equals(j.getProductTypeId()))
                .findFirst()
                .orElseThrow();
        WorkerTimeDto worker2 = job2.getWorkers().get(0);
        assertThat(worker2.getSpeedIndexContributionPercentage())
                .as("KONTENER FIRANOWY should contribute exactly 33.33% to speed index")
                .isEqualByComparingTo(new BigDecimal("33.33"));

        // Job 3: 50.00% contribution
        JobDto job3 = response.getJobs().stream()
                .filter(j -> "DRZWI STALOWE".equals(j.getProductTypeId()))
                .findFirst()
                .orElseThrow();
        WorkerTimeDto worker3 = job3.getWorkers().get(0);
        assertThat(worker3.getSpeedIndexContributionPercentage())
                .as("DRZWI STALOWE should contribute exactly 50.00% to speed index")
                .isEqualByComparingTo(new BigDecimal("50.00"));

        // Verify all contributions sum to 100%
        BigDecimal totalContribution = worker1.getSpeedIndexContributionPercentage()
                .add(worker2.getSpeedIndexContributionPercentage())
                .add(worker3.getSpeedIndexContributionPercentage());
        assertThat(totalContribution)
                .as("All speed index contributions should sum to exactly 100%")
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should calculate speed index contributions for team jobs (multiple workers)")
    void shouldCalculateSpeedIndexContributionsForTeamJobs() {
        // Given: Two workers working on same jobs with different contributions
        String worker1Id = "Jan Kowalski";
        String worker2Id = "Anna Nowak";

        /*
         * TEAM JOB SCENARIO:
         *
         * Job 1: KONTENER FIRANOWY (both workers)
         * - Benchmark: 20h/unit
         * - Jan's hours: 12h (720 minutes)
         * - Anna's hours: 8h (480 minutes)
         * - Total job hours: 20h
         * - Jan's contribution: 12h / 20h = 0.6
         * - Anna's contribution: 8h / 20h = 0.4
         * - Jan's numerator: 20 × 0.6 = 12
         * - Anna's numerator: 20 × 0.4 = 8
         *
         * Job 2: RAMA ALUMINIOWA (Jan solo)
         * - Benchmark: 10h/unit
         * - Jan's hours: 8h (480 minutes)
         * - Total job hours: 8h
         * - Jan's contribution: 8h / 8h = 1.0
         * - Jan's numerator: 10 × 1.0 = 10
         *
         * Job 3: DRZWI STALOWE (Anna solo)
         * - Benchmark: 15h/unit
         * - Anna's hours: 12h (720 minutes)
         * - Total job hours: 12h
         * - Anna's contribution: 12h / 12h = 1.0
         * - Anna's numerator: 15 × 1.0 = 15
         *
         * Jan's total numerator: 12 + 10 = 22
         * Anna's total numerator: 8 + 15 = 23
         *
         * Jan's Wpływ:
         * - Job 1: (12 / 22) × 100 = 54.55%
         * - Job 2: (10 / 22) × 100 = 45.45%
         * - SUM: 100.00% ✅
         *
         * Anna's Wpływ:
         * - Job 1: (8 / 23) × 100 = 34.78%
         * - Job 3: (15 / 23) × 100 = 65.22%
         * - SUM: 100.00% ✅
         */

        List<JobDto> jobs = Arrays.asList(
                JobDto.builder()
                        .id(1)
                        .numerZlecenia("ZP/00001/2024")
                        .date(LocalDate.of(2024, 10, 1))
                        .productTypeId("KONTENER FIRANOWY")
                        .quantity(BigDecimal.ONE)
                        .totalMinutes(BigDecimal.valueOf(1200)) // 20h total
                        .workers(Arrays.asList(
                                WorkerTimeDto.builder()
                                        .workerId(worker1Id)
                                        .resourceId(worker1Id)
                                        .minutesWorked(BigDecimal.valueOf(720)) // Jan: 12h
                                        .build(),
                                WorkerTimeDto.builder()
                                        .workerId(worker2Id)
                                        .resourceId(worker2Id)
                                        .minutesWorked(BigDecimal.valueOf(480)) // Anna: 8h
                                        .build()
                        ))
                        .build(),
                JobDto.builder()
                        .id(2)
                        .numerZlecenia("ZP/00002/2024")
                        .date(LocalDate.of(2024, 10, 2))
                        .productTypeId("RAMA ALUMINIOWA")
                        .quantity(BigDecimal.ONE)
                        .totalMinutes(BigDecimal.valueOf(480)) // 8h
                        .workers(Collections.singletonList(
                                WorkerTimeDto.builder()
                                        .workerId(worker1Id)
                                        .resourceId(worker1Id)
                                        .minutesWorked(BigDecimal.valueOf(480)) // Jan: 8h
                                        .build()
                        ))
                        .build(),
                JobDto.builder()
                        .id(3)
                        .numerZlecenia("ZP/00003/2024")
                        .date(LocalDate.of(2024, 10, 3))
                        .productTypeId("DRZWI STALOWE")
                        .quantity(BigDecimal.ONE)
                        .totalMinutes(BigDecimal.valueOf(720)) // 12h
                        .workers(Collections.singletonList(
                                WorkerTimeDto.builder()
                                        .workerId(worker2Id)
                                        .resourceId(worker2Id)
                                        .minutesWorked(BigDecimal.valueOf(720)) // Anna: 12h
                                        .build()
                        ))
                        .build()
        );

        testJobs = jobs;

        Map<String, BigDecimal> benchmarks = new HashMap<>();
        benchmarks.put("KONTENER FIRANOWY", new BigDecimal("20"));
        benchmarks.put("RAMA ALUMINIOWA", new BigDecimal("10"));
        benchmarks.put("DRZWI STALOWE", new BigDecimal("15"));

        // Mock dependencies
        when(workerAnalyticsCacheService.getAllJobs()).thenReturn(testJobs);
        when(workerAnalyticsCacheService.getAllEmployeeHoursMap()).thenReturn(Collections.emptyMap());
        when(workerStatsCalculator.filterJobs(any(), any(), any(), any(), any(), any())).thenReturn(testJobs);
        when(workerStatsCalculator.buildAttendanceMap(any())).thenReturn(Collections.emptyMap());
        when(workerStatsCalculator.calculateBenchmarks(any())).thenReturn(benchmarks);
        when(workerStatsCalculator.calculateAllWorkerStats(any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(workerStatsCalculator.collectAllCappedDays(any())).thenReturn(Collections.emptyList());

        // When: Get worker analytics
        WorkerAnalyticsResponseDto response = workerAnalyticsService.getWorkerAnalytics(
                WorkerAnalyticsRequestDto.builder()
                        .dateFrom(LocalDate.of(2024, 10, 1))
                        .dateTo(LocalDate.of(2024, 10, 31))
                        .build()
        );

        // Then: Verify Jan's contributions
        JobDto job1 = response.getJobs().stream()
                .filter(j -> "KONTENER FIRANOWY".equals(j.getProductTypeId()))
                .findFirst()
                .orElseThrow();
        WorkerTimeDto janOnJob1 = job1.getWorkers().stream()
                .filter(w -> worker1Id.equals(w.getWorkerId()))
                .findFirst()
                .orElseThrow();
        assertThat(janOnJob1.getSpeedIndexContributionPercentage())
                .as("Jan's contribution on KONTENER should be exactly 54.55%")
                .isEqualByComparingTo(new BigDecimal("54.55"));

        JobDto job2 = response.getJobs().stream()
                .filter(j -> "RAMA ALUMINIOWA".equals(j.getProductTypeId()))
                .findFirst()
                .orElseThrow();
        WorkerTimeDto janOnJob2 = job2.getWorkers().get(0);
        assertThat(janOnJob2.getSpeedIndexContributionPercentage())
                .as("Jan's contribution on RAMA should be exactly 45.45%")
                .isEqualByComparingTo(new BigDecimal("45.45"));

        // Verify Jan's contributions sum to 100%
        BigDecimal janTotal = janOnJob1.getSpeedIndexContributionPercentage()
                .add(janOnJob2.getSpeedIndexContributionPercentage());
        assertThat(janTotal)
                .as("Jan's total contributions should sum to exactly 100%")
                .isEqualByComparingTo(new BigDecimal("100.00"));

        // Verify Anna's contributions
        WorkerTimeDto annaOnJob1 = job1.getWorkers().stream()
                .filter(w -> worker2Id.equals(w.getWorkerId()))
                .findFirst()
                .orElseThrow();
        assertThat(annaOnJob1.getSpeedIndexContributionPercentage())
                .as("Anna's contribution on KONTENER should be exactly 34.78%")
                .isEqualByComparingTo(new BigDecimal("34.78"));

        JobDto job3 = response.getJobs().stream()
                .filter(j -> "DRZWI STALOWE".equals(j.getProductTypeId()))
                .findFirst()
                .orElseThrow();
        WorkerTimeDto annaOnJob3 = job3.getWorkers().get(0);
        assertThat(annaOnJob3.getSpeedIndexContributionPercentage())
                .as("Anna's contribution on DRZWI should be exactly 65.22%")
                .isEqualByComparingTo(new BigDecimal("65.22"));

        // Verify Anna's contributions sum to 100%
        BigDecimal annaTotal = annaOnJob1.getSpeedIndexContributionPercentage()
                .add(annaOnJob3.getSpeedIndexContributionPercentage());
        assertThat(annaTotal)
                .as("Anna's total contributions should sum to exactly 100%")
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }
}
