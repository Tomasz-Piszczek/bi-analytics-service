package org.example.bianalyticsservice.service;

import org.example.bianalyticsservice.controller.analytics.dto.*;
import org.example.bianalyticsservice.controller.employee.dto.DailyHoursDto;
import org.example.bianalyticsservice.controller.employee.dto.EmployeeHoursDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WorkerAnalyticsE2ETest {

    private WorkerStatsCalculator calculator;

    private static final String WORKER_KOWALSKI = "JAN.KOWALSKI";
    private static final String WORKER_NOWAK = "ANNA.NOWAK";
    private static final String WORKER_WISNIEWSKI = "PIOTR.WISNIEWSKI";
    private static final String WORKER_WOJCIK = "MARIA.WOJCIK";
    private static final String WORKER_KOWALCZYK = "TOMASZ.KOWALCZYK";
    private static final String WORKER_KAMINSKI = "EWA.KAMINSKA";

    private static final LocalDate DAY_1 = LocalDate.of(2024, 10, 1);
    private static final LocalDate DAY_2 = LocalDate.of(2024, 10, 2);
    private static final LocalDate DAY_3 = LocalDate.of(2024, 10, 3);
    private static final LocalDate DAY_4 = LocalDate.of(2024, 10, 4);
    private static final LocalDate DAY_5 = LocalDate.of(2024, 10, 5);

    @BeforeEach
    void setUp() {
        calculator = new WorkerStatsCalculator();
    }

    private void assertBDEquals(String expected, BigDecimal actual, String msg) {
        assertEquals(new BigDecimal(expected), actual.setScale(scaleOf(expected), RoundingMode.HALF_UP), msg);
    }

    private int scaleOf(String val) {
        int dot = val.indexOf('.');
        return dot < 0 ? 0 : val.length() - dot - 1;
    }

    private void assertBDEquals(String expected, BigDecimal actual) {
        assertBDEquals(expected, actual, null);
    }

    // ─── TEST: input data sanity ────────────────────────────────────────────
    @Test
    void testDataSanity() {
        List<JobDto> jobs = createMockedJobs();
        List<EmployeeHoursDto> attendance = createMockedAttendance();

        assertEquals(10, jobs.size(), "Should have 10 jobs");
        assertEquals(6, attendance.size(), "Should have 6 employees in attendance");

        int totalWorkerAssignments = jobs.stream()
                .mapToInt(j -> j.getWorkers().size())
                .sum();
        assertEquals(23, totalWorkerAssignments, "Total worker assignments across all jobs");
    }

    // ─── TEST: benchmark calculation ────────────────────────────────────────
    @Test
    void benchmarks_shouldBeAvgHoursPerUnit() {
        List<JobDto> jobs = createMockedJobs();
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);

        assertEquals(5, benchmarks.size(), "5 product types");
        assertBDEquals("1.0000", benchmarks.get("RAMA-STALOWA"));
        assertBDEquals("1.4167", benchmarks.get("DRZWI-ALUMINIOWE"));
        assertBDEquals("0.6667", benchmarks.get("OKNO-PCV"));
        assertBDEquals("8.0000", benchmarks.get("BRAMA-GARAZOWA"));
        assertBDEquals("10.0000", benchmarks.get("PRACE WEWNĘTRZNE"));
    }

    // ─── TEST: attendance map building ──────────────────────────────────────
    @Test
    void buildAttendanceMap_shouldMapCorrectly() {
        List<EmployeeHoursDto> attendance = createMockedAttendance();
        Map<String, Map<LocalDate, BigDecimal>> map = calculator.buildAttendanceMap(attendance);

        assertEquals(6, map.size());
        assertEquals(5, map.get(WORKER_KOWALSKI).size());
        assertEquals(new BigDecimal("8"), map.get(WORKER_KOWALSKI).get(DAY_1));
        assertEquals(4, map.get(WORKER_WISNIEWSKI).size());
        assertNull(map.get(WORKER_WISNIEWSKI).get(DAY_3));
        assertEquals(3, map.get(WORKER_WOJCIK).size());
    }

    // ─── TEST: full analytics (unfiltered, no cap triggered) ────────────────
    @Test
    void fullAnalytics_withInternalWork() {
        List<JobDto> jobs = createMockedJobs();
        List<EmployeeHoursDto> attendance = createMockedAttendance();

        Map<String, Map<LocalDate, BigDecimal>> attendanceMap = calculator.buildAttendanceMap(attendance);
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);
        List<WorkerStatsDto> stats = calculator.calculateAllWorkerStats(jobs, jobs, benchmarks, attendanceMap, false);

        printResults("ALL WORKERS - WITH INTERNAL WORK", stats, benchmarks);

        assertEquals(6, stats.size());

        WorkerStatsDto kowalski = findWorker(stats, WORKER_KOWALSKI);
        assertNotNull(kowalski);
        assertBDEquals("1.02", kowalski.getSpeedIndex(), "Kowalski SI");
        assertBDEquals("40.0", kowalski.getPresence(), "Kowalski presence");
        assertBDEquals("10.7", kowalski.getProduction(), "Kowalski production");
        assertBDEquals("2.0", kowalski.getInternalWork(), "Kowalski internal");
        assertBDEquals("27.3", kowalski.getIdle(), "Kowalski idle");
        assertEquals(5, kowalski.getJobCount(), "Kowalski job count");
        assertNotNull(kowalski.getDailyDetails(), "Kowalski should have daily details");
        assertEquals(5, kowalski.getDailyDetails().size(), "Kowalski 5 relevant days");
        assertTrue(kowalski.getCappedDays().isEmpty(), "No capped days in normal data");

        WorkerStatsDto nowak = findWorker(stats, WORKER_NOWAK);
        assertBDEquals("1.04", nowak.getSpeedIndex());
        assertBDEquals("40.0", nowak.getPresence());
        assertBDEquals("11.0", nowak.getProduction());
        assertBDEquals("1.5", nowak.getInternalWork());
        assertBDEquals("27.5", nowak.getIdle());
        assertEquals(5, nowak.getJobCount());

        WorkerStatsDto wisniewski = findWorker(stats, WORKER_WISNIEWSKI);
        assertBDEquals("0.93", wisniewski.getSpeedIndex());
        assertBDEquals("32.0", wisniewski.getPresence());
        assertBDEquals("12.7", wisniewski.getProduction());
        assertBDEquals("2.5", wisniewski.getInternalWork());
        assertBDEquals("16.8", wisniewski.getIdle());
        assertEquals(4, wisniewski.getJobCount());

        WorkerStatsDto wojcik = findWorker(stats, WORKER_WOJCIK);
        assertBDEquals("0.99", wojcik.getSpeedIndex());
        assertBDEquals("24.0", wojcik.getPresence());
        assertBDEquals("3.5", wojcik.getProduction());
        assertBDEquals("1.0", wojcik.getInternalWork());
        assertBDEquals("19.5", wojcik.getIdle());
        assertEquals(3, wojcik.getJobCount());

        WorkerStatsDto kowalczyk = findWorker(stats, WORKER_KOWALCZYK);
        assertBDEquals("0.99", kowalczyk.getSpeedIndex());
        assertBDEquals("24.0", kowalczyk.getPresence());
        assertBDEquals("3.5", kowalczyk.getProduction());
        assertBDEquals("1.3", kowalczyk.getInternalWork());
        assertBDEquals("19.2", kowalczyk.getIdle());
        assertEquals(3, kowalczyk.getJobCount());

        WorkerStatsDto kaminska = findWorker(stats, WORKER_KAMINSKI);
        assertBDEquals("1.05", kaminska.getSpeedIndex());
        assertBDEquals("24.0", kaminska.getPresence());
        assertBDEquals("4.3", kaminska.getProduction());
        assertBDEquals("1.7", kaminska.getInternalWork());
        assertBDEquals("18.0", kaminska.getIdle());
        assertEquals(3, kaminska.getJobCount());

        BigDecimal totalPresence = stats.stream().map(WorkerStatsDto::getPresence).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalProd = stats.stream().map(WorkerStatsDto::getProduction).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInternal = stats.stream().map(WorkerStatsDto::getInternalWork).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertBDEquals("184.0", totalPresence, "Total presence");
        assertBDEquals("45.7", totalProd, "Total production");
        assertBDEquals("10.0", totalInternal, "Total internal");
    }

    // ─── TEST: ignoreInternalWork flag ──────────────────────────────────────
    @Test
    void analytics_ignoreInternalWork_shouldZeroOutInternalAndAffectSpeedIndex() {
        List<JobDto> jobs = createMockedJobs();
        List<EmployeeHoursDto> attendance = createMockedAttendance();

        Map<String, Map<LocalDate, BigDecimal>> attendanceMap = calculator.buildAttendanceMap(attendance);
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);

        List<WorkerStatsDto> withInternal = calculator.calculateAllWorkerStats(jobs, jobs, benchmarks, attendanceMap, false);
        List<WorkerStatsDto> withoutInternal = calculator.calculateAllWorkerStats(jobs, jobs, benchmarks, attendanceMap, true);

        for (WorkerStatsDto s : withoutInternal) {
            assertBDEquals("0.0", s.getInternalWork(), s.getWorkerId() + " internal should be 0");
        }

        for (String wid : List.of(WORKER_KOWALSKI, WORKER_NOWAK, WORKER_WISNIEWSKI, WORKER_WOJCIK, WORKER_KOWALCZYK, WORKER_KAMINSKI)) {
            assertEquals(
                    findWorker(withInternal, wid).getProduction(),
                    findWorker(withoutInternal, wid).getProduction(),
                    wid + " production unchanged");
        }

        WorkerStatsDto kowalskiWith = findWorker(withInternal, WORKER_KOWALSKI);
        WorkerStatsDto kowalskiWithout = findWorker(withoutInternal, WORKER_KOWALSKI);
        BigDecimal expectedIdleIncrease = kowalskiWith.getInternalWork();
        BigDecimal actualIdleIncrease = kowalskiWithout.getIdle().subtract(kowalskiWith.getIdle());
        assertBDEquals(expectedIdleIncrease.setScale(1, RoundingMode.HALF_UP).toPlainString(),
                actualIdleIncrease, "Idle increases by removed internal hours");

        assertBDEquals("1.02", findWorker(withInternal, WORKER_KOWALSKI).getSpeedIndex());
        assertBDEquals("1.03", findWorker(withoutInternal, WORKER_KOWALSKI).getSpeedIndex());
        assertBDEquals("0.93", findWorker(withInternal, WORKER_WISNIEWSKI).getSpeedIndex());
        assertBDEquals("0.86", findWorker(withoutInternal, WORKER_WISNIEWSKI).getSpeedIndex());
        assertBDEquals("1.12", findWorker(withoutInternal, WORKER_KAMINSKI).getSpeedIndex());
        assertBDEquals("19.7", findWorker(withoutInternal, WORKER_KAMINSKI).getIdle());
    }

    // ─── TEST: date range filter (presence scoped to relevant dates) ────────
    @Test
    void analytics_withDateFilter_shouldOnlyIncludeDay1And2() {
        List<JobDto> allJobs = createMockedJobs();
        List<EmployeeHoursDto> attendance = createMockedAttendance();

        List<JobDto> filteredJobs = calculator.filterJobs(allJobs, DAY_1, DAY_2, null, null, false);
        assertEquals(5, filteredJobs.size(), "Jobs in day 1-2 range");

        Map<String, Map<LocalDate, BigDecimal>> attendanceMap = calculator.buildAttendanceMap(attendance);
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(filteredJobs);

        List<WorkerStatsDto> stats = calculator.calculateAllWorkerStats(filteredJobs, allJobs, benchmarks, attendanceMap, false);
        assertEquals(6, stats.size());

        // Presence scoped to relevant dates only
        assertBDEquals("16.0", findWorker(stats, WORKER_KOWALSKI).getPresence());
        assertBDEquals("16.0", findWorker(stats, WORKER_NOWAK).getPresence());
        assertBDEquals("16.0", findWorker(stats, WORKER_WISNIEWSKI).getPresence());
        assertBDEquals("8.0", findWorker(stats, WORKER_WOJCIK).getPresence());
        assertBDEquals("8.0", findWorker(stats, WORKER_KOWALCZYK).getPresence());
        assertBDEquals("8.0", findWorker(stats, WORKER_KAMINSKI).getPresence());

        // Idle = presence - all work on relevant dates
        assertBDEquals("11.8", findWorker(stats, WORKER_KOWALSKI).getIdle());
        assertBDEquals("10.2", findWorker(stats, WORKER_NOWAK).getIdle());
        assertBDEquals("8.7", findWorker(stats, WORKER_WISNIEWSKI).getIdle());
        assertBDEquals("6.5", findWorker(stats, WORKER_WOJCIK).getIdle());
        assertBDEquals("6.5", findWorker(stats, WORKER_KOWALCZYK).getIdle());
        assertBDEquals("6.3", findWorker(stats, WORKER_KAMINSKI).getIdle());
    }

    // ─── TEST: product filter ───────────────────────────────────────────────
    @Test
    void analytics_withProductFilter_shouldOnlyIncludeSelectedProducts() {
        List<JobDto> allJobs = createMockedJobs();
        List<EmployeeHoursDto> attendance = createMockedAttendance();

        Set<String> selectedProducts = new HashSet<>(Arrays.asList("RAMA-STALOWA", "DRZWI-ALUMINIOWE"));
        List<JobDto> filteredJobs = calculator.filterJobs(allJobs, null, null, selectedProducts, null, false);
        assertEquals(6, filteredJobs.size());

        Map<String, Map<LocalDate, BigDecimal>> attendanceMap = calculator.buildAttendanceMap(attendance);
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(filteredJobs);
        List<WorkerStatsDto> stats = calculator.calculateAllWorkerStats(filteredJobs, allJobs, benchmarks, attendanceMap, false);

        assertEquals(6, stats.size());
        assertBDEquals("0.75", findWorker(stats, WORKER_WISNIEWSKI).getSpeedIndex());
        assertEquals(1, findWorker(stats, WORKER_WISNIEWSKI).getJobCount());
        assertBDEquals("4.0", findWorker(stats, WORKER_WISNIEWSKI).getProduction());
        assertBDEquals("8.0", findWorker(stats, WORKER_WISNIEWSKI).getPresence());
        assertBDEquals("4.0", findWorker(stats, WORKER_WISNIEWSKI).getIdle());
        assertBDEquals("0.0", findWorker(stats, WORKER_KOWALSKI).getInternalWork());
    }

    // ─── TEST: excluded workers ─────────────────────────────────────────────
    @Test
    void analytics_withExcludedWorkers_shouldRemoveWorkersAndTheirJobs() {
        List<JobDto> allJobs = createMockedJobs();
        List<EmployeeHoursDto> attendance = createMockedAttendance();

        Set<String> excluded = new HashSet<>(Arrays.asList(WORKER_WISNIEWSKI, WORKER_KAMINSKI));
        List<JobDto> filteredJobs = calculator.filterJobs(allJobs, null, null, null, excluded, false);
        assertEquals(4, filteredJobs.size());

        Map<String, Map<LocalDate, BigDecimal>> attendanceMap = calculator.buildAttendanceMap(attendance);
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(filteredJobs);
        List<WorkerStatsDto> stats = calculator.calculateAllWorkerStats(filteredJobs, allJobs, benchmarks, attendanceMap, false);

        assertEquals(4, stats.size());
        assertNull(findWorker(stats, WORKER_WISNIEWSKI));
        assertNull(findWorker(stats, WORKER_KAMINSKI));

        assertEquals(3, findWorker(stats, WORKER_KOWALSKI).getJobCount());
        assertBDEquals("9.0", findWorker(stats, WORKER_KOWALSKI).getProduction());
        assertBDEquals("24.0", findWorker(stats, WORKER_KOWALSKI).getPresence());
        assertBDEquals("15.0", findWorker(stats, WORKER_KOWALSKI).getIdle());

        assertEquals(2, findWorker(stats, WORKER_NOWAK).getJobCount());
        assertBDEquals("5.0", findWorker(stats, WORKER_NOWAK).getProduction());
        assertBDEquals("16.0", findWorker(stats, WORKER_NOWAK).getPresence());
        assertBDEquals("11.0", findWorker(stats, WORKER_NOWAK).getIdle());
    }

    // ─── TEST: solo jobs filter ─────────────────────────────────────────────
    @Test
    void analytics_soloJobsOnly_shouldOnlyIncludeOneWorkerJobs() {
        List<JobDto> allJobs = createMockedJobs();
        List<EmployeeHoursDto> attendance = createMockedAttendance();

        List<JobDto> filteredJobs = calculator.filterJobs(allJobs, null, null, null, null, true);
        assertEquals(2, filteredJobs.size());

        Map<String, Map<LocalDate, BigDecimal>> attendanceMap = calculator.buildAttendanceMap(attendance);
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(filteredJobs);
        List<WorkerStatsDto> stats = calculator.calculateAllWorkerStats(filteredJobs, allJobs, benchmarks, attendanceMap, false);

        assertEquals(1, stats.size());
        WorkerStatsDto wisniewski = findWorker(stats, WORKER_WISNIEWSKI);
        assertNotNull(wisniewski);
        assertEquals(2, wisniewski.getJobCount());
        assertBDEquals("1.00", wisniewski.getSpeedIndex());
        assertBDEquals("16.0", wisniewski.getPresence());
        assertBDEquals("9.3", wisniewski.getProduction());
        assertBDEquals("0.0", wisniewski.getInternalWork());
        assertBDEquals("6.7", wisniewski.getIdle());
    }

    // ─── TEST: combined date + product filter ───────────────────────────────
    @Test
    void analytics_combinedDateAndProductFilter() {
        List<JobDto> jobs = createMockedJobs();

        Set<String> products = new HashSet<>(Collections.singletonList("RAMA-STALOWA"));
        List<JobDto> filtered = calculator.filterJobs(jobs, DAY_1, DAY_3, products, null, false);

        assertEquals(3, filtered.size());
        for (JobDto j : filtered) {
            assertEquals("RAMA-STALOWA", j.getProductTypeId());
            assertTrue(!j.getDate().isBefore(DAY_1) && !j.getDate().isAfter(DAY_3));
        }
    }

    // ─── TEST: empty filter result ──────────────────────────────────────────
    @Test
    void analytics_filterReturnsNoJobs_shouldReturnEmptyStats() {
        List<JobDto> allJobs = createMockedJobs();
        List<EmployeeHoursDto> attendance = createMockedAttendance();

        Set<String> nonExistent = new HashSet<>(Collections.singletonList("NON-EXISTENT"));
        List<JobDto> filtered = calculator.filterJobs(allJobs, null, null, nonExistent, null, false);
        assertEquals(0, filtered.size());

        Map<String, Map<LocalDate, BigDecimal>> attendanceMap = calculator.buildAttendanceMap(attendance);
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(filtered);
        List<WorkerStatsDto> stats = calculator.calculateAllWorkerStats(filtered, allJobs, benchmarks, attendanceMap, false);

        assertEquals(0, benchmarks.size());
        assertEquals(0, stats.size());
    }

    // ─── TEST: speed index ordering ─────────────────────────────────────────
    @Test
    void speedIndex_fastWorkerAlwaysAboveSlow() {
        List<JobDto> jobs = createMockedJobs();
        Map<String, Map<LocalDate, BigDecimal>> attendanceMap = calculator.buildAttendanceMap(createMockedAttendance());
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);
        List<WorkerStatsDto> stats = calculator.calculateAllWorkerStats(jobs, jobs, benchmarks, attendanceMap, false);

        WorkerStatsDto kaminska = findWorker(stats, WORKER_KAMINSKI);
        WorkerStatsDto wisniewski = findWorker(stats, WORKER_WISNIEWSKI);

        assertTrue(kaminska.getSpeedIndex().compareTo(wisniewski.getSpeedIndex()) > 0);
        assertTrue(kaminska.getSpeedIndex().compareTo(BigDecimal.ONE) > 0);
        assertTrue(wisniewski.getSpeedIndex().compareTo(BigDecimal.ONE) < 0);
    }

    // ─── TEST: idle time non-negative ───────────────────────────────────────
    @Test
    void idleTime_shouldNeverBeNegative() {
        List<JobDto> jobs = createMockedJobs();
        Map<String, Map<LocalDate, BigDecimal>> attendanceMap = calculator.buildAttendanceMap(createMockedAttendance());
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);
        List<WorkerStatsDto> stats = calculator.calculateAllWorkerStats(jobs, jobs, benchmarks, attendanceMap, false);

        for (WorkerStatsDto s : stats) {
            assertTrue(s.getIdle().compareTo(BigDecimal.ZERO) >= 0, s.getWorkerId() + " idle >= 0");
            assertTrue(s.getPresence().compareTo(BigDecimal.ZERO) >= 0, s.getWorkerId() + " presence >= 0");

            BigDecimal sum = s.getProduction().add(s.getInternalWork()).add(s.getIdle());
            assertTrue(s.getPresence().subtract(sum).abs().compareTo(new BigDecimal("0.1")) < 0,
                    s.getWorkerId() + " presence ≈ prod+internal+idle");
        }
    }

    // ─── TEST: percentages sum to ~100% ─────────────────────────────────────
    @Test
    void percentages_shouldAddUpTo100_forFilteredScenario() {
        List<JobDto> allJobs = createMockedJobs();

        Set<String> products = new HashSet<>(Collections.singletonList("RAMA-STALOWA"));
        List<JobDto> filteredJobs = calculator.filterJobs(allJobs, null, null, products, null, false);

        Map<String, Map<LocalDate, BigDecimal>> attendanceMap = calculator.buildAttendanceMap(createMockedAttendance());
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(filteredJobs);
        List<WorkerStatsDto> stats = calculator.calculateAllWorkerStats(filteredJobs, allJobs, benchmarks, attendanceMap, false);

        for (WorkerStatsDto s : stats) {
            if (s.getPresence().compareTo(BigDecimal.ZERO) == 0) continue;

            BigDecimal prodPct = s.getProduction().multiply(new BigDecimal("100"))
                    .divide(s.getPresence(), 1, RoundingMode.HALF_UP);
            BigDecimal internalPct = s.getInternalWork().multiply(new BigDecimal("100"))
                    .divide(s.getPresence(), 1, RoundingMode.HALF_UP);
            BigDecimal idlePct = s.getIdle().multiply(new BigDecimal("100"))
                    .divide(s.getPresence(), 1, RoundingMode.HALF_UP);
            BigDecimal totalPct = prodPct.add(internalPct).add(idlePct);

            assertTrue(totalPct.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("1.0")) < 0,
                    s.getWorkerId() + " percentages should sum to ~100% but got " + totalPct + "%");
        }
    }

    // ─── TEST: daily details structure ──────────────────────────────────────
    @Test
    void dailyDetails_shouldHaveCorrectStructure() {
        List<JobDto> jobs = createMockedJobs();
        Map<String, Map<LocalDate, BigDecimal>> attendanceMap = calculator.buildAttendanceMap(createMockedAttendance());
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);
        List<WorkerStatsDto> stats = calculator.calculateAllWorkerStats(jobs, jobs, benchmarks, attendanceMap, false);

        WorkerStatsDto kowalski = findWorker(stats, WORKER_KOWALSKI);
        List<DailyWorkerDetailDto> details = kowalski.getDailyDetails();

        assertEquals(5, details.size(), "Kowalski has 5 days");
        // Sorted by date
        for (int i = 1; i < details.size(); i++) {
            assertTrue(details.get(i).getDate().isAfter(details.get(i - 1).getDate()), "Sorted by date");
        }

        // DAY_1: job1 → 150min/60 = 2.5h production, att=8h, idle=5.5h
        DailyWorkerDetailDto day1 = details.get(0);
        assertEquals(DAY_1, day1.getDate());
        assertBDEquals("2.50", day1.getProductionHours());
        assertBDEquals("0.00", day1.getInternalHours());
        assertBDEquals("5.50", day1.getIdleHours());
        assertBDEquals("8.00", day1.getAttendanceHours());
        assertFalse(day1.isWasCapped());

        // DAY_5: job10 → 120min/60 = 2.0h internal, att=8h, idle=6.0h
        DailyWorkerDetailDto day5 = details.get(4);
        assertEquals(DAY_5, day5.getDate());
        assertBDEquals("0.00", day5.getProductionHours());
        assertBDEquals("2.00", day5.getInternalHours());
        assertBDEquals("6.00", day5.getIdleHours());
        assertBDEquals("8.00", day5.getAttendanceHours());
        assertFalse(day5.isWasCapped());

        // Each day: prod + internal + idle ≈ attendance
        for (DailyWorkerDetailDto d : details) {
            BigDecimal sum = d.getProductionHours().add(d.getInternalHours()).add(d.getIdleHours());
            BigDecimal diff = d.getAttendanceHours().subtract(sum).abs();
            assertTrue(diff.compareTo(new BigDecimal("0.01")) < 0,
                    "Day " + d.getDate() + ": prod+internal+idle should equal attendance");
        }
    }

    // ─── TEST: daily cap with bad data ──────────────────────────────────────
    @Test
    void dailyCap_shouldCapAt10h_andScaleProportionally() {
        // Create a scenario with extreme hours on one day
        LocalDate badDay = LocalDate.of(2024, 11, 1);
        LocalDate normalDay = LocalDate.of(2024, 11, 2);
        String worker = "BAD.DATA.WORKER";

        // Bad day: 1200 minutes (20h) production + 0 internal
        // Normal day: 300 minutes (5h) production
        List<JobDto> jobs = Arrays.asList(
                JobDto.builder()
                        .id(100)
                        .numerZlecenia("BAD/001")
                        .date(badDay)
                        .productTypeId("PRODUCT-A")
                        .quantity(new BigDecimal("10"))
                        .totalMinutes(new BigDecimal("1200"))
                        .workers(Arrays.asList(
                                WorkerTimeDto.builder().workerId(worker).workDate(badDay).minutesWorked(new BigDecimal("1200")).build()
                        ))
                        .build(),
                JobDto.builder()
                        .id(101)
                        .numerZlecenia("NORMAL/001")
                        .date(normalDay)
                        .productTypeId("PRODUCT-A")
                        .quantity(new BigDecimal("5"))
                        .totalMinutes(new BigDecimal("300"))
                        .workers(Arrays.asList(
                                WorkerTimeDto.builder().workerId(worker).workDate(normalDay).minutesWorked(new BigDecimal("300")).build()
                        ))
                        .build()
        );

        // Attendance: 8h both days
        List<EmployeeHoursDto> attendance = Collections.singletonList(
                EmployeeHoursDto.builder()
                        .employeeName(worker)
                        .dailyHours(Arrays.asList(
                                DailyHoursDto.builder().date(badDay).hours(new BigDecimal("8")).build(),
                                DailyHoursDto.builder().date(normalDay).hours(new BigDecimal("8")).build()
                        ))
                        .build()
        );

        Map<String, Map<LocalDate, BigDecimal>> attendanceMap = calculator.buildAttendanceMap(attendance);
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);
        List<WorkerStatsDto> stats = calculator.calculateAllWorkerStats(jobs, jobs, benchmarks, attendanceMap, false);

        assertEquals(1, stats.size());
        WorkerStatsDto ws = stats.get(0);

        printResults("CAP TEST", stats, benchmarks);

        // Bad day: work=20h, attendance=8h → presence=max(8,20)=20 → capped to 10
        //          scale factor = 10/20 = 0.5 → production = 20*0.5 = 10h, idle = 0
        // Normal day: work=5h, attendance=8h → presence=8h, prod=5h, idle=3h
        // Totals: presence=18h, production=15h, idle=3h

        assertBDEquals("18.0", ws.getPresence(), "Presence: 10 (capped) + 8 (normal)");
        assertBDEquals("15.0", ws.getProduction(), "Production: 10 (scaled) + 5 (normal)");
        assertBDEquals("0.0", ws.getInternalWork());
        assertBDEquals("3.0", ws.getIdle(), "Idle: 0 (capped day) + 3 (normal day)");

        // Capped days reported
        assertEquals(1, ws.getCappedDays().size(), "One capped day");
        CappedDayDto capped = ws.getCappedDays().get(0);
        assertEquals(worker, capped.getWorkerId());
        assertEquals(badDay, capped.getDate());
        assertBDEquals("20.00", capped.getOriginalHours());
        assertBDEquals("10.00", capped.getCappedHours());

        // Daily details
        assertEquals(2, ws.getDailyDetails().size());
        DailyWorkerDetailDto badDayDetail = ws.getDailyDetails().stream()
                .filter(d -> d.getDate().equals(badDay)).findFirst().orElseThrow();
        assertTrue(badDayDetail.isWasCapped());
        assertBDEquals("10.00", badDayDetail.getProductionHours(), "Production capped to 10h");
        assertBDEquals("0.00", badDayDetail.getIdleHours());

        DailyWorkerDetailDto normalDayDetail = ws.getDailyDetails().stream()
                .filter(d -> d.getDate().equals(normalDay)).findFirst().orElseThrow();
        assertFalse(normalDayDetail.isWasCapped());
        assertBDEquals("5.00", normalDayDetail.getProductionHours());
        assertBDEquals("3.00", normalDayDetail.getIdleHours());

        // Percentages add up
        BigDecimal prodPct = ws.getProduction().multiply(new BigDecimal("100"))
                .divide(ws.getPresence(), 1, RoundingMode.HALF_UP);
        BigDecimal idlePct = ws.getIdle().multiply(new BigDecimal("100"))
                .divide(ws.getPresence(), 1, RoundingMode.HALF_UP);
        BigDecimal total = prodPct.add(idlePct);
        assertTrue(total.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("1")) < 0,
                "Percentages should sum to ~100% but got " + total + "%");
    }

    // ─── TEST: cap with production + internal both exceeding ────────────────
    @Test
    void dailyCap_shouldScaleProductionAndInternalProportionally() {
        LocalDate day = LocalDate.of(2024, 11, 1);
        String worker = "MIXED.WORKER";

        // 900 min production (15h) + 300 min internal (5h) = 20h total
        List<JobDto> jobs = Arrays.asList(
                JobDto.builder()
                        .id(200)
                        .numerZlecenia("PROD/001")
                        .date(day)
                        .productTypeId("PRODUCT-B")
                        .quantity(new BigDecimal("1"))
                        .totalMinutes(new BigDecimal("900"))
                        .workers(Arrays.asList(
                                WorkerTimeDto.builder().workerId(worker).workDate(day).minutesWorked(new BigDecimal("900")).build()
                        ))
                        .build(),
                JobDto.builder()
                        .id(201)
                        .numerZlecenia("PRACE-WEWN/001")
                        .date(day)
                        .productTypeId("PRACE WEWNĘTRZNE")
                        .quantity(new BigDecimal("1"))
                        .totalMinutes(new BigDecimal("300"))
                        .workers(Arrays.asList(
                                WorkerTimeDto.builder().workerId(worker).workDate(day).minutesWorked(new BigDecimal("300")).build()
                        ))
                        .build()
        );

        List<EmployeeHoursDto> attendance = Collections.singletonList(
                EmployeeHoursDto.builder()
                        .employeeName(worker)
                        .dailyHours(Collections.singletonList(
                                DailyHoursDto.builder().date(day).hours(new BigDecimal("0")).build()
                        ))
                        .build()
        );

        Map<String, Map<LocalDate, BigDecimal>> attendanceMap = calculator.buildAttendanceMap(attendance);
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);
        List<WorkerStatsDto> stats = calculator.calculateAllWorkerStats(jobs, jobs, benchmarks, attendanceMap, false);

        WorkerStatsDto ws = stats.get(0);

        // Total work = 20h, attendance = 0h → presence = max(0, 20) = 20 → capped to 10
        // Scale factor = 10/20 = 0.5
        // Production = 15 * 0.5 = 7.5h, Internal = 5 * 0.5 = 2.5h, idle = 0
        assertBDEquals("10.0", ws.getPresence());
        assertBDEquals("7.5", ws.getProduction(), "Production scaled: 15 * 0.5");
        assertBDEquals("2.5", ws.getInternalWork(), "Internal scaled: 5 * 0.5");
        assertBDEquals("0.0", ws.getIdle());

        assertEquals(1, ws.getCappedDays().size());
        assertBDEquals("20.00", ws.getCappedDays().get(0).getOriginalHours());
    }

    // ─── TEST: cap with zero attendance but work ────────────────────────────
    @Test
    void dailyCap_zeroAttendanceWithWork_shouldUseWorkAsBasis() {
        LocalDate day = LocalDate.of(2024, 11, 1);
        String worker = "NO.RCP.WORKER";

        // 480 min (8h) production, 0 attendance
        List<JobDto> jobs = Collections.singletonList(
                JobDto.builder()
                        .id(300)
                        .numerZlecenia("X/001")
                        .date(day)
                        .productTypeId("PRODUCT-C")
                        .quantity(new BigDecimal("1"))
                        .totalMinutes(new BigDecimal("480"))
                        .workers(Collections.singletonList(
                                WorkerTimeDto.builder().workerId(worker).workDate(day).minutesWorked(new BigDecimal("480")).build()
                        ))
                        .build()
        );

        // No attendance data at all
        Map<String, Map<LocalDate, BigDecimal>> attendanceMap = new HashMap<>();
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);
        List<WorkerStatsDto> stats = calculator.calculateAllWorkerStats(jobs, jobs, benchmarks, attendanceMap, false);

        WorkerStatsDto ws = stats.get(0);

        // Work = 8h, attendance = 0 → presence = max(0, 8) = 8h (under cap, no capping)
        assertBDEquals("8.0", ws.getPresence());
        assertBDEquals("8.0", ws.getProduction());
        assertBDEquals("0.0", ws.getIdle());
        assertTrue(ws.getCappedDays().isEmpty(), "8h under cap, no capping needed");
    }

    // ─── TEST: collectAllCappedDays aggregation ─────────────────────────────
    @Test
    void collectAllCappedDays_shouldAggregateFromAllWorkers() {
        List<JobDto> jobs = createMockedJobs();
        Map<String, Map<LocalDate, BigDecimal>> attendanceMap = calculator.buildAttendanceMap(createMockedAttendance());
        Map<String, BigDecimal> benchmarks = calculator.calculateBenchmarks(jobs);
        List<WorkerStatsDto> stats = calculator.calculateAllWorkerStats(jobs, jobs, benchmarks, attendanceMap, false);

        // Normal data has no capped days
        List<CappedDayDto> cappedDays = calculator.collectAllCappedDays(stats);
        assertEquals(0, cappedDays.size(), "No capped days in normal test data");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATA BUILDERS
    // ═══════════════════════════════════════════════════════════════════════

    private List<JobDto> createMockedJobs() {
        List<JobDto> jobs = new ArrayList<>();
        int id = 1;

        jobs.add(JobDto.builder().id(id++).numerZlecenia("ZLC/2024/001").date(DAY_1).productTypeId("RAMA-STALOWA")
                .quantity(new BigDecimal("5")).totalMinutes(new BigDecimal("300"))
                .workers(Arrays.asList(
                        WorkerTimeDto.builder().workerId(WORKER_KOWALSKI).workDate(DAY_1).minutesWorked(new BigDecimal("150")).build(),
                        WorkerTimeDto.builder().workerId(WORKER_NOWAK).workDate(DAY_1).minutesWorked(new BigDecimal("150")).build()
                )).build());

        jobs.add(JobDto.builder().id(id++).numerZlecenia("ZLC/2024/002").date(DAY_1).productTypeId("RAMA-STALOWA")
                .quantity(new BigDecimal("3")).totalMinutes(new BigDecimal("240"))
                .workers(Arrays.asList(
                        WorkerTimeDto.builder().workerId(WORKER_WISNIEWSKI).workDate(DAY_1).minutesWorked(new BigDecimal("240")).build()
                )).build());

        jobs.add(JobDto.builder().id(id++).numerZlecenia("ZLC/2024/003").date(DAY_1).productTypeId("DRZWI-ALUMINIOWE")
                .quantity(new BigDecimal("2")).totalMinutes(new BigDecimal("180"))
                .workers(Arrays.asList(
                        WorkerTimeDto.builder().workerId(WORKER_WOJCIK).workDate(DAY_1).minutesWorked(new BigDecimal("90")).build(),
                        WorkerTimeDto.builder().workerId(WORKER_KOWALCZYK).workDate(DAY_1).minutesWorked(new BigDecimal("90")).build()
                )).build());

        jobs.add(JobDto.builder().id(id++).numerZlecenia("ZLC/2024/004").date(DAY_2).productTypeId("RAMA-STALOWA")
                .quantity(new BigDecimal("4")).totalMinutes(new BigDecimal("200"))
                .workers(Arrays.asList(
                        WorkerTimeDto.builder().workerId(WORKER_KOWALSKI).workDate(DAY_2).minutesWorked(new BigDecimal("100")).build(),
                        WorkerTimeDto.builder().workerId(WORKER_KAMINSKI).workDate(DAY_2).minutesWorked(new BigDecimal("100")).build()
                )).build());

        jobs.add(JobDto.builder().id(id++).numerZlecenia("ZLC/2024/005").date(DAY_2).productTypeId("OKNO-PCV")
                .quantity(new BigDecimal("10")).totalMinutes(new BigDecimal("400"))
                .workers(Arrays.asList(
                        WorkerTimeDto.builder().workerId(WORKER_NOWAK).workDate(DAY_2).minutesWorked(new BigDecimal("200")).build(),
                        WorkerTimeDto.builder().workerId(WORKER_WISNIEWSKI).workDate(DAY_2).minutesWorked(new BigDecimal("200")).build()
                )).build());

        jobs.add(JobDto.builder().id(id++).numerZlecenia("ZLC/2024/006").date(DAY_3).productTypeId("BRAMA-GARAZOWA")
                .quantity(new BigDecimal("1")).totalMinutes(new BigDecimal("480"))
                .workers(Arrays.asList(
                        WorkerTimeDto.builder().workerId(WORKER_KOWALSKI).workDate(DAY_3).minutesWorked(new BigDecimal("240")).build(),
                        WorkerTimeDto.builder().workerId(WORKER_WOJCIK).workDate(DAY_3).minutesWorked(new BigDecimal("120")).build(),
                        WorkerTimeDto.builder().workerId(WORKER_KOWALCZYK).workDate(DAY_3).minutesWorked(new BigDecimal("120")).build()
                )).build());

        jobs.add(JobDto.builder().id(id++).numerZlecenia("ZLC/2024/007").date(DAY_3).productTypeId("DRZWI-ALUMINIOWE")
                .quantity(new BigDecimal("4")).totalMinutes(new BigDecimal("320"))
                .workers(Arrays.asList(
                        WorkerTimeDto.builder().workerId(WORKER_NOWAK).workDate(DAY_3).minutesWorked(new BigDecimal("160")).build(),
                        WorkerTimeDto.builder().workerId(WORKER_KAMINSKI).workDate(DAY_3).minutesWorked(new BigDecimal("160")).build()
                )).build());

        jobs.add(JobDto.builder().id(id++).numerZlecenia("ZLC/2024/008").date(DAY_4).productTypeId("OKNO-PCV")
                .quantity(new BigDecimal("8")).totalMinutes(new BigDecimal("320"))
                .workers(Arrays.asList(
                        WorkerTimeDto.builder().workerId(WORKER_WISNIEWSKI).workDate(DAY_4).minutesWorked(new BigDecimal("320")).build()
                )).build());

        jobs.add(JobDto.builder().id(id++).numerZlecenia("ZLC/2024/009").date(DAY_4).productTypeId("RAMA-STALOWA")
                .quantity(new BigDecimal("6")).totalMinutes(new BigDecimal("300"))
                .workers(Arrays.asList(
                        WorkerTimeDto.builder().workerId(WORKER_KOWALSKI).workDate(DAY_4).minutesWorked(new BigDecimal("150")).build(),
                        WorkerTimeDto.builder().workerId(WORKER_NOWAK).workDate(DAY_4).minutesWorked(new BigDecimal("150")).build()
                )).build());

        jobs.add(JobDto.builder().id(id++).numerZlecenia("ZLC/2024/010-PRACE-WEWN").date(DAY_5).productTypeId("PRACE WEWNĘTRZNE")
                .quantity(new BigDecimal("1")).totalMinutes(new BigDecimal("600"))
                .workers(Arrays.asList(
                        WorkerTimeDto.builder().workerId(WORKER_KOWALSKI).workDate(DAY_5).minutesWorked(new BigDecimal("120")).build(),
                        WorkerTimeDto.builder().workerId(WORKER_NOWAK).workDate(DAY_5).minutesWorked(new BigDecimal("90")).build(),
                        WorkerTimeDto.builder().workerId(WORKER_WISNIEWSKI).workDate(DAY_5).minutesWorked(new BigDecimal("150")).build(),
                        WorkerTimeDto.builder().workerId(WORKER_WOJCIK).workDate(DAY_5).minutesWorked(new BigDecimal("60")).build(),
                        WorkerTimeDto.builder().workerId(WORKER_KOWALCZYK).workDate(DAY_5).minutesWorked(new BigDecimal("80")).build(),
                        WorkerTimeDto.builder().workerId(WORKER_KAMINSKI).workDate(DAY_5).minutesWorked(new BigDecimal("100")).build()
                )).build());

        return jobs;
    }

    private List<EmployeeHoursDto> createMockedAttendance() {
        return Arrays.asList(
                EmployeeHoursDto.builder().employeeName(WORKER_KOWALSKI).dailyHours(Arrays.asList(
                        DailyHoursDto.builder().date(DAY_1).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_2).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_3).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_4).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_5).hours(new BigDecimal("8")).build()
                )).build(),
                EmployeeHoursDto.builder().employeeName(WORKER_NOWAK).dailyHours(Arrays.asList(
                        DailyHoursDto.builder().date(DAY_1).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_2).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_3).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_4).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_5).hours(new BigDecimal("8")).build()
                )).build(),
                EmployeeHoursDto.builder().employeeName(WORKER_WISNIEWSKI).dailyHours(Arrays.asList(
                        DailyHoursDto.builder().date(DAY_1).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_2).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_4).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_5).hours(new BigDecimal("8")).build()
                )).build(),
                EmployeeHoursDto.builder().employeeName(WORKER_WOJCIK).dailyHours(Arrays.asList(
                        DailyHoursDto.builder().date(DAY_1).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_3).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_5).hours(new BigDecimal("8")).build()
                )).build(),
                EmployeeHoursDto.builder().employeeName(WORKER_KOWALCZYK).dailyHours(Arrays.asList(
                        DailyHoursDto.builder().date(DAY_1).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_3).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_5).hours(new BigDecimal("8")).build()
                )).build(),
                EmployeeHoursDto.builder().employeeName(WORKER_KAMINSKI).dailyHours(Arrays.asList(
                        DailyHoursDto.builder().date(DAY_2).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_3).hours(new BigDecimal("8")).build(),
                        DailyHoursDto.builder().date(DAY_5).hours(new BigDecimal("8")).build()
                )).build()
        );
    }

    private WorkerStatsDto findWorker(List<WorkerStatsDto> stats, String workerId) {
        return stats.stream().filter(s -> workerId.equals(s.getWorkerId())).findFirst().orElse(null);
    }

    private void printResults(String title, List<WorkerStatsDto> stats, Map<String, BigDecimal> benchmarks) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println(title);
        System.out.println("=".repeat(80));

        System.out.println("\nBENCHMARKS:");
        benchmarks.forEach((p, h) -> System.out.printf("  %-25s : %s h/unit%n", p, h));

        System.out.println("\nWORKER STATS:");
        System.out.printf("%-20s | %6s | %8s | %8s | %8s | %8s | %4s | %6s%n",
                "WORKER", "SPEED", "PRESENCE", "PROD", "INTERNAL", "IDLE", "JOBS", "CAPPED");
        System.out.println("-".repeat(85));

        for (WorkerStatsDto s : stats) {
            System.out.printf("%-20s | %6s | %6s h | %6s h | %6s h | %6s h | %4d | %4d%n",
                    s.getWorkerId(),
                    s.getSpeedIndex() != null ? s.getSpeedIndex() : "N/A",
                    s.getPresence(), s.getProduction(), s.getInternalWork(), s.getIdle(),
                    s.getJobCount(), s.getCappedDays().size());
        }
        System.out.println("-".repeat(85));
    }
}