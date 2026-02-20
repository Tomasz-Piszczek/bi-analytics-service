package org.example.bianalyticsservice.service;

import org.example.bianalyticsservice.controller.analytics.dto.JobDto;
import org.example.bianalyticsservice.controller.analytics.dto.WorkerStatsDto;
import org.example.bianalyticsservice.controller.analytics.dto.WorkerTimeDto;
import org.example.bianalyticsservice.controller.employee.dto.DailyHoursDto;
import org.example.bianalyticsservice.controller.employee.dto.EmployeeHoursDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WorkerStatsCalculatorTest {

    private WorkerStatsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new WorkerStatsCalculator();
    }

    @Test
    void isInternalWorkJob_shouldReturnTrue_forPraceWewnetrzne() {
        JobDto job = JobDto.builder()
                .productTypeId("PRACE WEWNĘTRZNE")
                .build();

        assertTrue(calculator.isInternalWorkJob(job));
    }

    @Test
    void isInternalWorkJob_shouldReturnTrue_forPraceWewnVariant() {
        JobDto job = JobDto.builder()
                .productTypeId("PRACE WEWN - SERWIS")
                .build();

        assertTrue(calculator.isInternalWorkJob(job));
    }

    @Test
    void isInternalWorkJob_shouldReturnFalse_forRegularProduct() {
        JobDto job = JobDto.builder()
                .productTypeId("PRODUKT A")
                .build();

        assertFalse(calculator.isInternalWorkJob(job));
    }

    @Test
    void calculateBenchmarks_shouldCalculateAveragePerProduct() {
        List<JobDto> jobs = Arrays.asList(
                createJob("PRODUCT-A", 60, 1),
                createJob("PRODUCT-A", 120, 1),
                createJob("PRODUCT-B", 90, 1)
        );

        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);

        assertEquals(new BigDecimal("1.5000"), benchmarks.get("PRODUCT-A"));
        assertEquals(new BigDecimal("1.5000"), benchmarks.get("PRODUCT-B"));
    }

    @Test
    void calculateBenchmarks_shouldDivideByQuantity() {
        List<JobDto> jobs = Arrays.asList(
                createJob("PRODUCT-A", 60, 2),
                createJob("PRODUCT-A", 120, 4)
        );

        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);

        assertEquals(new BigDecimal("0.5000"), benchmarks.get("PRODUCT-A"));
    }

    @Test
    void getHoursPerUnit_shouldCalculateCorrectly() {
        JobDto job = createJob("PRODUCT-A", 120, 2);

        BigDecimal hoursPerUnit = calculator.getHoursPerUnit(job);

        assertEquals(new BigDecimal("1.0000"), hoursPerUnit);
    }

    @Test
    void getWorkerHoursPerUnit_shouldCalculateCorrectly() {
        WorkerTimeDto worker = WorkerTimeDto.builder()
                .workerId("WORKER-1")
                .minutesWorked(new BigDecimal(120))
                .build();

        BigDecimal hoursPerUnit = calculator.getWorkerHoursPerUnit(worker, new BigDecimal(2));

        assertEquals(new BigDecimal("1.0000"), hoursPerUnit);
    }

    @Test
    void calculateSpeedIndex_shouldReturnCorrectRatio() {
        List<JobDto> jobs = Arrays.asList(
                createJobWithWorker("PRODUCT-A", 60, 1, "WORKER-1", 60),
                createJobWithWorker("PRODUCT-A", 120, 1, "WORKER-1", 120)
        );

        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);
        BigDecimal speedIndex = calculator.calculateSpeedIndex("WORKER-1", jobs, benchmarks, false);

        assertEquals(new BigDecimal("1.00"), speedIndex);
    }

    @Test
    void calculateSpeedIndex_shouldBeFaster_whenWorkerIsFast() {
        List<JobDto> jobs = Arrays.asList(
                createJobWithWorker("PRODUCT-A", 60, 1, "WORKER-1", 60),
                createJobWithWorker("PRODUCT-A", 180, 1, "WORKER-2", 180)
        );

        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);

        BigDecimal speedIndex1 = calculator.calculateSpeedIndex("WORKER-1", jobs, benchmarks, false);
        BigDecimal speedIndex2 = calculator.calculateSpeedIndex("WORKER-2", jobs, benchmarks, false);

        assertEquals(new BigDecimal("2.00"), speedIndex1);
        assertEquals(new BigDecimal("0.67"), speedIndex2);
    }

    @Test
    void calculateSpeedIndex_shouldAccountForQuantity() {
        List<JobDto> jobs = Arrays.asList(
                createJobWithWorker("PRODUCT-A", 60, 2, "WORKER-1", 60),
                createJobWithWorker("PRODUCT-A", 120, 2, "WORKER-2", 120)
        );

        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);

        BigDecimal speedIndex1 = calculator.calculateSpeedIndex("WORKER-1", jobs, benchmarks, false);
        BigDecimal speedIndex2 = calculator.calculateSpeedIndex("WORKER-2", jobs, benchmarks, false);

        assertEquals(new BigDecimal("1.50"), speedIndex1);
        assertEquals(new BigDecimal("0.75"), speedIndex2);
    }

    @Test
    void calculateSpeedIndex_shouldIgnoreInternalWork_whenFlagSet() {
        List<JobDto> jobs = Arrays.asList(
                createJobWithWorker("PRODUCT-A", 60, 1, "WORKER-1", 60),
                createJobWithWorker("PRACE WEWNĘTRZNE", 120, 1, "WORKER-1", 120)
        );

        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);

        BigDecimal withInternal = calculator.calculateSpeedIndex("WORKER-1", jobs, benchmarks, false);
        BigDecimal withoutInternal = calculator.calculateSpeedIndex("WORKER-1", jobs, benchmarks, true);

        assertNotNull(withInternal);
        assertNotNull(withoutInternal);
    }

    @Test
    void calculateSpeedIndex_shouldReturnNull_whenNoJobs() {
        List<JobDto> jobs = Collections.emptyList();
        Map<String, BigDecimal> benchmarks = new HashMap<>();

        BigDecimal speedIndex = calculator.calculateSpeedIndex("WORKER-1", jobs, benchmarks, false);

        assertNull(speedIndex);
    }

    @Test
    void calculateWorkerStats_shouldCalculateAllStats() {
        List<JobDto> jobs = Arrays.asList(
                createJobWithWorkerAndDate("PRODUCT-A", 60, 1, "WORKER-1", 60, LocalDate.of(2024, 1, 15))
        );

        List<EmployeeHoursDto> employeeHours = Arrays.asList(
                createEmployeeHours("WORKER-1", LocalDate.of(2024, 1, 15), new BigDecimal("8"))
        );

        Map<String, Map<LocalDate, BigDecimal>> attendance = calculator.buildAttendanceMap(employeeHours);
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);

        WorkerStatsDto stats = calculator.calculateWorkerStats("WORKER-1", jobs, jobs, benchmarks, attendance, false);

        assertEquals("WORKER-1", stats.getWorkerId());
        assertEquals(1, stats.getJobCount());
        assertEquals(new BigDecimal("8.0"), stats.getPresence());
        assertTrue(stats.getProduction().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void calculateWorkerStats_shouldCapPresenceAt10Hours_whenOver11() {
        List<JobDto> jobs = Arrays.asList(
                createJobWithWorkerAndDate("PRODUCT-A", 60, 1, "WORKER-1", 60, LocalDate.of(2024, 1, 15))
        );

        List<EmployeeHoursDto> employeeHours = Arrays.asList(
                createEmployeeHours("WORKER-1", LocalDate.of(2024, 1, 15), new BigDecimal("12"))
        );

        Map<String, Map<LocalDate, BigDecimal>> attendance = calculator.buildAttendanceMap(employeeHours);
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);

        WorkerStatsDto stats = calculator.calculateWorkerStats("WORKER-1", jobs, jobs,benchmarks, attendance, false);

        assertEquals(new BigDecimal("10.0"), stats.getPresence());
    }

    @Test
    void calculateWorkerStats_shouldCalculateIdleTime() {
        List<JobDto> jobs = Arrays.asList(
                createJobWithWorkerAndDate("PRODUCT-A", 60, 1, "WORKER-1", 60, LocalDate.of(2024, 1, 15))
        );

        List<EmployeeHoursDto> employeeHours = Arrays.asList(
                createEmployeeHours("WORKER-1", LocalDate.of(2024, 1, 15), new BigDecimal("8"))
        );

        Map<String, Map<LocalDate, BigDecimal>> attendance = calculator.buildAttendanceMap(employeeHours);
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);

        WorkerStatsDto stats = calculator.calculateWorkerStats("WORKER-1", jobs,jobs, benchmarks, attendance, false);

        assertTrue(stats.getIdle().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    void calculateWorkerStats_shouldTrackInternalWork() {
        List<JobDto> jobs = Arrays.asList(
                createJobWithWorkerAndDate("PRACE WEWNĘTRZNE", 120, 1, "WORKER-1", 120, LocalDate.of(2024, 1, 15))
        );

        List<EmployeeHoursDto> employeeHours = Arrays.asList(
                createEmployeeHours("WORKER-1", LocalDate.of(2024, 1, 15), new BigDecimal("8"))
        );

        Map<String, Map<LocalDate, BigDecimal>> attendance = calculator.buildAttendanceMap(employeeHours);
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);

        WorkerStatsDto statsWithInternal = calculator.calculateWorkerStats("WORKER-1", jobs, jobs,benchmarks, attendance, false);
        WorkerStatsDto statsIgnoreInternal = calculator.calculateWorkerStats("WORKER-1", jobs,jobs, benchmarks, attendance, true);

        assertTrue(statsWithInternal.getInternalWork().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(new BigDecimal("0.0"), statsIgnoreInternal.getInternalWork());
    }

    @Test
    void filterJobs_shouldFilterByWorkerWorkDate() {
        List<JobDto> jobs = Arrays.asList(
                createJobWithDate("PRODUCT-A", LocalDate.of(2024, 1, 10)),
                createJobWithDate("PRODUCT-A", LocalDate.of(2024, 1, 20)),
                createJobWithDate("PRODUCT-A", LocalDate.of(2024, 1, 30))
        );

        List<JobDto> filtered = calculator.filterJobs(
                jobs,
                LocalDate.of(2024, 1, 15),
                LocalDate.of(2024, 1, 25),
                null, null, false
        );

        assertEquals(1, filtered.size());
    }

    @Test
    void filterJobs_shouldIncludeJob_whenWorkerWorkDateInRange_butJobDateOutside() {
        JobDto job = JobDto.builder()
                .id(jobIdCounter++)
                .productTypeId("PRODUCT-A")
                .date(LocalDate.of(2024, 1, 1))
                .totalMinutes(new BigDecimal(180))
                .quantity(BigDecimal.ONE)
                .workers(Arrays.asList(
                        WorkerTimeDto.builder().workerId("WORKER-1").workDate(LocalDate.of(2024, 1, 5)).minutesWorked(new BigDecimal(60)).build(),
                        WorkerTimeDto.builder().workerId("WORKER-1").workDate(LocalDate.of(2024, 1, 15)).minutesWorked(new BigDecimal(60)).build(),
                        WorkerTimeDto.builder().workerId("WORKER-1").workDate(LocalDate.of(2024, 1, 25)).minutesWorked(new BigDecimal(60)).build()
                ))
                .build();

        List<JobDto> filtered = calculator.filterJobs(
                Collections.singletonList(job),
                LocalDate.of(2024, 1, 10),
                LocalDate.of(2024, 1, 20),
                null, null, false
        );

        assertEquals(1, filtered.size());
        assertEquals(1, filtered.get(0).getWorkers().size());
        assertEquals(new BigDecimal(60), filtered.get(0).getTotalMinutes());
    }

    @Test
    void filterJobs_shouldExcludeJob_whenAllWorkerWorkDatesOutsideRange() {
        JobDto job = JobDto.builder()
                .id(jobIdCounter++)
                .productTypeId("PRODUCT-A")
                .date(LocalDate.of(2024, 1, 15))
                .totalMinutes(new BigDecimal(120))
                .quantity(BigDecimal.ONE)
                .workers(Arrays.asList(
                        WorkerTimeDto.builder().workerId("WORKER-1").workDate(LocalDate.of(2024, 1, 1)).minutesWorked(new BigDecimal(60)).build(),
                        WorkerTimeDto.builder().workerId("WORKER-1").workDate(LocalDate.of(2024, 1, 5)).minutesWorked(new BigDecimal(60)).build()
                ))
                .build();

        List<JobDto> filtered = calculator.filterJobs(
                Collections.singletonList(job),
                LocalDate.of(2024, 1, 10),
                LocalDate.of(2024, 1, 20),
                null, null, false
        );

        assertEquals(0, filtered.size());
    }

    @Test
    void filterJobs_shouldFilterBySelectedProducts() {
        List<JobDto> jobs = Arrays.asList(
                createJob("PRODUCT-A", 60, 1),
                createJob("PRODUCT-B", 60, 1),
                createJob("PRODUCT-C", 60, 1)
        );

        Set<String> selectedProducts = new HashSet<>(Arrays.asList("PRODUCT-A", "PRODUCT-B"));

        List<JobDto> filtered = calculator.filterJobs(
                jobs, null, null, selectedProducts, null, false
        );

        assertEquals(2, filtered.size());
    }

    @Test
    void filterJobs_shouldFilterByExcludedWorkers() {
        List<JobDto> jobs = Arrays.asList(
                createJobWithWorker("PRODUCT-A", 60, 1, "WORKER-1", 60),
                createJobWithWorker("PRODUCT-A", 60, 1, "WORKER-2", 60)
        );

        Set<String> excludedWorkers = Collections.singleton("WORKER-1");

        List<JobDto> filtered = calculator.filterJobs(
                jobs, null, null, null, excludedWorkers, false
        );

        assertEquals(1, filtered.size());
        assertEquals("WORKER-2", filtered.get(0).getWorkers().get(0).getWorkerId());
    }

    @Test
    void filterJobs_shouldFilterSoloOnly() {
        List<JobDto> jobs = Arrays.asList(
                createJobWithMultipleWorkers("PRODUCT-A", 60, 1, Arrays.asList("WORKER-1", "WORKER-2")),
                createJobWithWorker("PRODUCT-A", 60, 1, "WORKER-1", 60)
        );

        List<JobDto> filtered = calculator.filterJobs(
                jobs, null, null, null, null, true
        );

        assertEquals(1, filtered.size());
        assertEquals(1, filtered.get(0).getWorkers().size());
    }

    @Test
    void buildAttendanceMap_shouldBuildCorrectMap() {
        List<EmployeeHoursDto> employeeHours = Arrays.asList(
                createEmployeeHours("WORKER-1", LocalDate.of(2024, 1, 15), new BigDecimal("8")),
                createEmployeeHours("WORKER-1", LocalDate.of(2024, 1, 16), new BigDecimal("7"))
        );

        Map<String, Map<LocalDate, BigDecimal>> attendance = calculator.buildAttendanceMap(employeeHours);

        assertTrue(attendance.containsKey("WORKER-1"));
        assertEquals(new BigDecimal("8"), attendance.get("WORKER-1").get(LocalDate.of(2024, 1, 15)));
    }

    @Test
    void calculateAllWorkerStats_shouldReturnSortedBySpeedIndex() {
        List<JobDto> jobs = Arrays.asList(
                createJobWithWorker("PRODUCT-A", 60, 1, "SLOW-WORKER", 60),
                createJobWithWorker("PRODUCT-A", 180, 1, "FAST-WORKER", 180)
        );

        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);
        Map<String, Map<LocalDate, BigDecimal>> attendance = new HashMap<>();

        List<WorkerStatsDto> stats = calculator.calculateAllWorkerStats(jobs, jobs,benchmarks, attendance, false);

        assertEquals(2, stats.size());
        assertEquals("SLOW-WORKER", stats.get(0).getWorkerId());
        assertEquals(new BigDecimal("2.00"), stats.get(0).getSpeedIndex());
        assertEquals("FAST-WORKER", stats.get(1).getWorkerId());
        assertEquals(new BigDecimal("0.67"), stats.get(1).getSpeedIndex());
    }

    private static int jobIdCounter = 1;

    private JobDto createJob(String productId, int totalMinutes, int quantity) {
        return JobDto.builder()
                .id(jobIdCounter++)
                .productTypeId(productId)
                .totalMinutes(new BigDecimal(totalMinutes))
                .quantity(new BigDecimal(quantity))
                .workers(Collections.emptyList())
                .build();
    }

    private JobDto createJobWithDate(String productId, LocalDate date) {
        return JobDto.builder()
                .id(jobIdCounter++)
                .productTypeId(productId)
                .date(date)
                .totalMinutes(new BigDecimal(60))
                .quantity(BigDecimal.ONE)
                .workers(Collections.singletonList(
                        WorkerTimeDto.builder().workerId("WORKER-1").workDate(date).minutesWorked(new BigDecimal(60)).build()
                ))
                .build();
    }

    private JobDto createJobWithWorker(String productId, int totalMinutes, int quantity, String workerId, int workerMinutes) {
        return JobDto.builder()
                .id(jobIdCounter++)
                .productTypeId(productId)
                .totalMinutes(new BigDecimal(totalMinutes))
                .quantity(new BigDecimal(quantity))
                .workers(Collections.singletonList(
                        WorkerTimeDto.builder().workerId(workerId).minutesWorked(new BigDecimal(workerMinutes)).build()
                ))
                .build();
    }

    private JobDto createJobWithWorkerAndDate(String productId, int totalMinutes, int quantity,
                                               String workerId, int workerMinutes, LocalDate date) {
        return JobDto.builder()
                .id(jobIdCounter++)
                .productTypeId(productId)
                .date(date)
                .totalMinutes(new BigDecimal(totalMinutes))
                .quantity(new BigDecimal(quantity))
                .workers(Collections.singletonList(
                        WorkerTimeDto.builder().workerId(workerId).workDate(date).minutesWorked(new BigDecimal(workerMinutes)).build()
                ))
                .build();
    }

    private JobDto createJobWithMultipleWorkers(String productId, int totalMinutes, int quantity, List<String> workerIds) {
        List<WorkerTimeDto> workers = workerIds.stream()
                .map(id -> WorkerTimeDto.builder()
                        .workerId(id)
                        .minutesWorked(new BigDecimal(totalMinutes / workerIds.size()))
                        .build())
                .toList();

        return JobDto.builder()
                .id(jobIdCounter++)
                .productTypeId(productId)
                .totalMinutes(new BigDecimal(totalMinutes))
                .quantity(new BigDecimal(quantity))
                .workers(workers)
                .build();
    }

    private EmployeeHoursDto createEmployeeHours(String employeeName, LocalDate date, BigDecimal hours) {
        return EmployeeHoursDto.builder()
                .employeeName(employeeName)
                .dailyHours(Collections.singletonList(
                        DailyHoursDto.builder().date(date).hours(hours).build()
                ))
                .build();
    }
}
