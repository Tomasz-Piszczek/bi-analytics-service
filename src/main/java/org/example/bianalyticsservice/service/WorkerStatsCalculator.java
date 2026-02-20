package org.example.bianalyticsservice.service;

import lombok.RequiredArgsConstructor;
import org.example.bianalyticsservice.controller.analytics.dto.*;
import org.example.bianalyticsservice.controller.employee.dto.DailyHoursDto;
import org.example.bianalyticsservice.controller.employee.dto.EmployeeHoursDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkerStatsCalculator {

    private static final Pattern INTERNAL_WORK_PATTERN = Pattern.compile("PRACE\\s*WEWN", Pattern.CASE_INSENSITIVE);
    private static final BigDecimal DAILY_PRESENCE_CAP = new BigDecimal("10");

    public boolean isInternalWorkJob(JobDto job) {
        return INTERNAL_WORK_PATTERN.matcher(job.getProductTypeId()).find();
    }

    public Map<String, BigDecimal> calculateBenchmarks(List<JobDto> jobs) {
        Map<String, List<BigDecimal>> hoursPerProduct = new HashMap<>();

        for (JobDto job : jobs) {
            hoursPerProduct.computeIfAbsent(job.getProductTypeId(), k -> new ArrayList<>()).add(getHoursPerUnit(job));
        }

        Map<String, BigDecimal> benchmarks = new HashMap<>();
        for (Map.Entry<String, List<BigDecimal>> entry : hoursPerProduct.entrySet()) {
            List<BigDecimal> hours = entry.getValue();
            BigDecimal sum = hours.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(new BigDecimal(hours.size()), 4, RoundingMode.HALF_UP);
            benchmarks.put(entry.getKey(), avg);
        }

        return benchmarks;
    }

    public BigDecimal getHoursPerUnit(JobDto job) {
        BigDecimal quantity = job.getQuantity().compareTo(BigDecimal.ZERO) > 0 ? job.getQuantity() : BigDecimal.ONE;
        return job.getTotalMinutes().divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP).divide(quantity, 4, RoundingMode.HALF_UP);
    }

    public BigDecimal getWorkerHoursPerUnit(WorkerTimeDto worker, BigDecimal quantity) {
        BigDecimal qty = quantity.compareTo(BigDecimal.ZERO) > 0 ? quantity : BigDecimal.ONE;
        return worker.getMinutesWorked().divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP).divide(qty, 4, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateSpeedIndex(
            String workerId,
            List<JobDto> jobs,
            Map<String, BigDecimal> benchmarks,
            boolean ignoreInternalWork
    ) {
        BigDecimal sumExp = BigDecimal.ZERO;
        BigDecimal sumAct = BigDecimal.ZERO;

        for (JobDto job : jobs) {
            if (ignoreInternalWork && isInternalWorkJob(job)) {
                continue;
            }

            // Find worker entries excluding group resource work (e.g., Wycinanie should not affect speed index)
            WorkerTimeDto worker = findWorkerExcludingGroupResource(job, workerId);
            if (worker == null) {
                continue;
            }

            BigDecimal hoursPerUnit = getHoursPerUnit(job);
            BigDecimal workerHours = getWorkerHoursPerUnit(worker, job.getQuantity());

            if (hoursPerUnit.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            BigDecimal benchmark = benchmarks.getOrDefault(job.getProductTypeId(), hoursPerUnit);
            BigDecimal workerContribution = workerHours.divide(hoursPerUnit, 4, RoundingMode.HALF_UP);

            sumExp = sumExp.add(benchmark.multiply(workerContribution));
            sumAct = sumAct.add(workerHours);
        }

        if (sumAct.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return sumExp.divide(sumAct, 2, RoundingMode.HALF_UP);
    }

    public WorkerStatsDto calculateWorkerStats(
            String workerId,
            List<JobDto> filteredJobs,
            List<JobDto> allJobs,
            Map<String, BigDecimal> benchmarks,
            Map<String, Map<LocalDate, BigDecimal>> attendance,
            boolean ignoreInternalWork
    ) {
        BigDecimal speedIndex = calculateSpeedIndex(workerId, filteredJobs, benchmarks, ignoreInternalWork);

        // 1. Determine relevant dates and job count from filtered jobs
        Set<LocalDate> relevantDates = new LinkedHashSet<>();
        int jobCount = 0;
        Set<Integer> processedJobIds = new HashSet<>();

        for (JobDto job : filteredJobs) {
            List<WorkerTimeDto> entries = findWorkerEntries(job, workerId);
            if (entries.isEmpty()) {
                continue;
            }

            if (processedJobIds.add(job.getId())) {
                jobCount++;
            }

            for (WorkerTimeDto entry : entries) {
                LocalDate workDate = entry.getWorkDate() != null ? entry.getWorkDate() : job.getDate();
                if (workDate != null) {
                    relevantDates.add(workDate);
                }
            }
        }

        // 2. Compute ALL work from allJobs on relevant dates
        Map<LocalDate, BigDecimal> allProductionPerDay = new HashMap<>();
        Map<LocalDate, BigDecimal> allInternalPerDay = new HashMap<>();

        for (JobDto job : allJobs) {
            List<WorkerTimeDto> entries = findWorkerEntries(job, workerId);
            if (entries.isEmpty()) {
                continue;
            }

            boolean isInternal = isInternalWorkJob(job);

            for (WorkerTimeDto entry : entries) {
                LocalDate workDate = entry.getWorkDate() != null ? entry.getWorkDate() : job.getDate();
                if (workDate == null || !relevantDates.contains(workDate)) {
                    continue;
                }

                BigDecimal hours = entry.getMinutesWorked()
                        .divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP);

                if (isInternal) {
                    allInternalPerDay.merge(workDate, hours, BigDecimal::add);
                } else {
                    allProductionPerDay.merge(workDate, hours, BigDecimal::add);
                }
            }
        }

        // 3. Compute totals with daily cap, collect daily details and capped days
        BigDecimal totalPresence = BigDecimal.ZERO;
        BigDecimal totalProduction = BigDecimal.ZERO;
        BigDecimal totalInternalWork = BigDecimal.ZERO;
        BigDecimal totalIdle = BigDecimal.ZERO;
        List<DailyWorkerDetailDto> dailyDetails = new ArrayList<>();
        List<CappedDayDto> cappedDays = new ArrayList<>();

        Map<LocalDate, BigDecimal> workerAttendance = attendance.getOrDefault(workerId, Collections.emptyMap());

        for (LocalDate date : relevantDates) {
            BigDecimal rcpHours = workerAttendance.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal dayProduction = allProductionPerDay.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal dayInternal = ignoreInternalWork
                    ? BigDecimal.ZERO
                    : allInternalPerDay.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal dailyWork = dayProduction.add(dayInternal);

            // Presence = max(attendance, actualWork) — if they worked, they were present
            BigDecimal presenceHours = rcpHours.max(dailyWork);

            // Track original values before cap for reporting
            BigDecimal originalDailyWork = dailyWork;
            boolean wasCapped = false;

            // Cap at DAILY_PRESENCE_CAP (10h)
            if (presenceHours.compareTo(DAILY_PRESENCE_CAP) > 0) {
                wasCapped = true;

                // Record original hours before capping
                cappedDays.add(CappedDayDto.builder()
                        .workerId(workerId)
                        .date(date)
                        .originalHours(presenceHours.setScale(2, RoundingMode.HALF_UP))
                        .cappedHours(DAILY_PRESENCE_CAP.setScale(2, RoundingMode.HALF_UP))
                        .build());

                presenceHours = DAILY_PRESENCE_CAP;

                // If work itself exceeds cap, scale production and internal proportionally
                if (dailyWork.compareTo(DAILY_PRESENCE_CAP) > 0) {
                    BigDecimal scaleFactor = DAILY_PRESENCE_CAP.divide(dailyWork, 4, RoundingMode.HALF_UP);
                    dayProduction = dayProduction.multiply(scaleFactor).setScale(4, RoundingMode.HALF_UP);
                    dayInternal = dayInternal.multiply(scaleFactor).setScale(4, RoundingMode.HALF_UP);
                }
            }

            BigDecimal dayIdle = presenceHours.subtract(dayProduction).subtract(dayInternal);
            if (dayIdle.compareTo(BigDecimal.ZERO) < 0) {
                dayIdle = BigDecimal.ZERO;
            }

            totalPresence = totalPresence.add(presenceHours);
            totalProduction = totalProduction.add(dayProduction);
            totalInternalWork = totalInternalWork.add(dayInternal);
            totalIdle = totalIdle.add(dayIdle);

            dailyDetails.add(DailyWorkerDetailDto.builder()
                    .date(date)
                    .productionHours(dayProduction.setScale(2, RoundingMode.HALF_UP))
                    .internalHours(dayInternal.setScale(2, RoundingMode.HALF_UP))
                    .idleHours(dayIdle.setScale(2, RoundingMode.HALF_UP))
                    .attendanceHours(rcpHours.setScale(2, RoundingMode.HALF_UP))
                    .wasCapped(wasCapped)
                    .build());
        }

        dailyDetails.sort(Comparator.comparing(DailyWorkerDetailDto::getDate));

        return WorkerStatsDto.builder()
                .workerId(workerId)
                .speedIndex(speedIndex)
                .presence(totalPresence.setScale(1, RoundingMode.HALF_UP))
                .production(totalProduction.setScale(1, RoundingMode.HALF_UP))
                .internalWork(totalInternalWork.setScale(1, RoundingMode.HALF_UP))
                .idle(totalIdle.setScale(1, RoundingMode.HALF_UP))
                .jobCount(jobCount)
                .dailyDetails(dailyDetails)
                .cappedDays(cappedDays)
                .build();
    }

    /**
     * A simple record to hold worker+resource combination as a key.
     */
    private record WorkerResourceKey(String workerId, String resourceId) {}

    public List<WorkerStatsDto> calculateAllWorkerStats(
            List<JobDto> filteredJobs,
            List<JobDto> allJobs,
            Map<String, BigDecimal> benchmarks,
            Map<String, Map<LocalDate, BigDecimal>> attendance,
            boolean ignoreInternalWork
    ) {
        // Collect unique (workerId, resourceId) pairs from filtered jobs
        Set<WorkerResourceKey> workerResourceKeys = filteredJobs.stream()
                .flatMap(job -> job.getWorkers().stream())
                .map(w -> new WorkerResourceKey(w.getWorkerId(), w.getResourceId() != null ? w.getResourceId() : w.getWorkerId()))
                .collect(Collectors.toSet());

        return workerResourceKeys.stream()
                .map(key -> calculateWorkerResourceStats(key.workerId(), key.resourceId(), filteredJobs, allJobs, benchmarks, attendance, ignoreInternalWork))
                .sorted((a, b) -> {
                    if (a.getSpeedIndex() == null && b.getSpeedIndex() == null) return 0;
                    if (a.getSpeedIndex() == null) return 1;
                    if (b.getSpeedIndex() == null) return -1;
                    return b.getSpeedIndex().compareTo(a.getSpeedIndex());
                })
                .collect(Collectors.toList());
    }

    /**
     * Calculate stats for a specific worker+resource combination.
     */
    public WorkerStatsDto calculateWorkerResourceStats(
            String workerId,
            String resourceId,
            List<JobDto> filteredJobs,
            List<JobDto> allJobs,
            Map<String, BigDecimal> benchmarks,
            Map<String, Map<LocalDate, BigDecimal>> attendance,
            boolean ignoreInternalWork
    ) {
        // Filter to only entries matching this worker+resource combination
        BigDecimal speedIndex = calculateSpeedIndexForResource(workerId, resourceId, filteredJobs, benchmarks, ignoreInternalWork);

        // 1. Determine relevant dates and job count from filtered jobs (only for this resource)
        Set<LocalDate> relevantDates = new LinkedHashSet<>();
        int jobCount = 0;
        Set<Integer> processedJobIds = new HashSet<>();

        for (JobDto job : filteredJobs) {
            List<WorkerTimeDto> entries = findWorkerResourceEntries(job, workerId, resourceId);
            if (entries.isEmpty()) {
                continue;
            }

            if (processedJobIds.add(job.getId())) {
                jobCount++;
            }

            for (WorkerTimeDto entry : entries) {
                LocalDate workDate = entry.getWorkDate() != null ? entry.getWorkDate() : job.getDate();
                if (workDate != null) {
                    relevantDates.add(workDate);
                }
            }
        }

        // 2. Compute TOTAL work by this workerId (across ALL resources) on relevant dates
        // Since it's the same physical person, we show totals regardless of which resource was used
        Map<LocalDate, BigDecimal> productionPerDay = new HashMap<>();
        Map<LocalDate, BigDecimal> internalPerDay = new HashMap<>();

        for (JobDto job : allJobs) {
            // Get ALL entries for this worker regardless of resource
            List<WorkerTimeDto> allWorkerEntries = findWorkerEntries(job, workerId);

            boolean isInternal = isInternalWorkJob(job);

            // Count ALL work by this worker (not just for this resource!)
            for (WorkerTimeDto entry : allWorkerEntries) {
                LocalDate workDate = entry.getWorkDate() != null ? entry.getWorkDate() : job.getDate();
                if (workDate == null || !relevantDates.contains(workDate)) {
                    continue;
                }

                BigDecimal hours = entry.getMinutesWorked()
                        .divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP);

                if (isInternal) {
                    internalPerDay.merge(workDate, hours, BigDecimal::add);
                } else {
                    productionPerDay.merge(workDate, hours, BigDecimal::add);
                }
            }
        }

        // 3. Compute totals with daily cap
        BigDecimal totalPresence = BigDecimal.ZERO;
        BigDecimal totalProduction = BigDecimal.ZERO;
        BigDecimal totalInternalWork = BigDecimal.ZERO;
        BigDecimal totalIdle = BigDecimal.ZERO;
        List<DailyWorkerDetailDto> dailyDetails = new ArrayList<>();
        List<CappedDayDto> cappedDays = new ArrayList<>();

        Map<LocalDate, BigDecimal> workerAttendance = attendance.getOrDefault(workerId, Collections.emptyMap());

        for (LocalDate date : relevantDates) {
            BigDecimal rcpHours = workerAttendance.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal dayProduction = productionPerDay.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal dayInternal = ignoreInternalWork
                    ? BigDecimal.ZERO
                    : internalPerDay.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal dailyWork = dayProduction.add(dayInternal);

            // Presence = max(attendance, actual work)
            BigDecimal presenceHours = rcpHours.max(dailyWork);

            boolean wasCapped = false;

            if (presenceHours.compareTo(DAILY_PRESENCE_CAP) > 0) {
                wasCapped = true;

                cappedDays.add(CappedDayDto.builder()
                        .workerId(workerId)
                        .date(date)
                        .originalHours(presenceHours.setScale(2, RoundingMode.HALF_UP))
                        .cappedHours(DAILY_PRESENCE_CAP.setScale(2, RoundingMode.HALF_UP))
                        .build());

                presenceHours = DAILY_PRESENCE_CAP;

                // Scale work proportionally if capped
                if (dailyWork.compareTo(DAILY_PRESENCE_CAP) > 0) {
                    BigDecimal scaleFactor = DAILY_PRESENCE_CAP.divide(dailyWork, 4, RoundingMode.HALF_UP);
                    dayProduction = dayProduction.multiply(scaleFactor).setScale(4, RoundingMode.HALF_UP);
                    dayInternal = dayInternal.multiply(scaleFactor).setScale(4, RoundingMode.HALF_UP);
                }
            }

            // Idle = presence - production - internal
            BigDecimal dayIdle = presenceHours.subtract(dayProduction).subtract(dayInternal);
            if (dayIdle.compareTo(BigDecimal.ZERO) < 0) {
                dayIdle = BigDecimal.ZERO;
            }

            totalPresence = totalPresence.add(presenceHours);
            totalProduction = totalProduction.add(dayProduction);
            totalInternalWork = totalInternalWork.add(dayInternal);
            totalIdle = totalIdle.add(dayIdle);

            dailyDetails.add(DailyWorkerDetailDto.builder()
                    .date(date)
                    .productionHours(dayProduction.setScale(2, RoundingMode.HALF_UP))
                    .internalHours(dayInternal.setScale(2, RoundingMode.HALF_UP))
                    .idleHours(dayIdle.setScale(2, RoundingMode.HALF_UP))
                    .attendanceHours(rcpHours.setScale(2, RoundingMode.HALF_UP))
                    .wasCapped(wasCapped)
                    .build());
        }

        dailyDetails.sort(Comparator.comparing(DailyWorkerDetailDto::getDate));

        return WorkerStatsDto.builder()
                .workerId(workerId)
                .resourceId(resourceId)
                .speedIndex(speedIndex)
                .presence(totalPresence.setScale(1, RoundingMode.HALF_UP))
                .production(totalProduction.setScale(1, RoundingMode.HALF_UP))
                .internalWork(totalInternalWork.setScale(1, RoundingMode.HALF_UP))
                .idle(totalIdle.setScale(1, RoundingMode.HALF_UP))
                .jobCount(jobCount)
                .dailyDetails(dailyDetails)
                .cappedDays(cappedDays)
                .build();
    }

    /**
     * Calculate speed index for a specific worker+resource combination.
     */
    private BigDecimal calculateSpeedIndexForResource(
            String workerId,
            String resourceId,
            List<JobDto> jobs,
            Map<String, BigDecimal> benchmarks,
            boolean ignoreInternalWork
    ) {
        BigDecimal sumExp = BigDecimal.ZERO;
        BigDecimal sumAct = BigDecimal.ZERO;

        for (JobDto job : jobs) {
            if (ignoreInternalWork && isInternalWorkJob(job)) {
                continue;
            }

            // Find worker entries for this specific resource
            List<WorkerTimeDto> entries = findWorkerResourceEntries(job, workerId, resourceId);
            if (entries.isEmpty()) {
                continue;
            }

            BigDecimal totalMinutes = entries.stream()
                    .map(WorkerTimeDto::getMinutesWorked)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            WorkerTimeDto worker = WorkerTimeDto.builder()
                    .workerId(workerId)
                    .resourceId(resourceId)
                    .minutesWorked(totalMinutes)
                    .build();

            BigDecimal hoursPerUnit = getHoursPerUnit(job);
            BigDecimal workerHours = getWorkerHoursPerUnit(worker, job.getQuantity());

            if (hoursPerUnit.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            BigDecimal benchmark = benchmarks.getOrDefault(job.getProductTypeId(), hoursPerUnit);
            BigDecimal workerContribution = workerHours.divide(hoursPerUnit, 4, RoundingMode.HALF_UP);

            sumExp = sumExp.add(benchmark.multiply(workerContribution));
            sumAct = sumAct.add(workerHours);
        }

        if (sumAct.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return sumExp.divide(sumAct, 2, RoundingMode.HALF_UP);
    }

    /**
     * Find worker entries matching a specific workerId AND resourceId.
     */
    private List<WorkerTimeDto> findWorkerResourceEntries(JobDto job, String workerId, String resourceId) {
        return job.getWorkers().stream()
                .filter(w -> workerId.equals(w.getWorkerId()))
                .filter(w -> {
                    String entryResourceId = w.getResourceId() != null ? w.getResourceId() : w.getWorkerId();
                    return resourceId.equals(entryResourceId);
                })
                .toList();
    }

    /**
     * Aggregates all capped days from all worker stats for the top-level response.
     */
    public List<CappedDayDto> collectAllCappedDays(List<WorkerStatsDto> workerStats) {
        return workerStats.stream()
                .filter(ws -> ws.getCappedDays() != null && !ws.getCappedDays().isEmpty())
                .flatMap(ws -> ws.getCappedDays().stream())
                .sorted(Comparator.comparing(CappedDayDto::getOriginalHours).reversed())
                .collect(Collectors.toList());
    }

    public Map<String, Map<LocalDate, BigDecimal>> buildAttendanceMap(List<EmployeeHoursDto> employeeHours) {
        Map<String, Map<LocalDate, BigDecimal>> attendance = new HashMap<>();

        for (EmployeeHoursDto emp : employeeHours) {
            Map<LocalDate, BigDecimal> dailyMap = attendance.computeIfAbsent(emp.getEmployeeName(), k -> new HashMap<>());
            for (DailyHoursDto daily : emp.getDailyHours()) {
                dailyMap.put(daily.getDate(), daily.getHours());
            }
        }

        return attendance;
    }

    private WorkerTimeDto findWorker(JobDto job, String workerId) {
        List<WorkerTimeDto> workerEntries = job.getWorkers().stream()
                .filter(w -> workerId.equals(w.getWorkerId()))
                .toList();

        if (workerEntries.isEmpty()) {
            return null;
        }

        BigDecimal totalMinutes = workerEntries.stream()
                .map(WorkerTimeDto::getMinutesWorked)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return WorkerTimeDto.builder()
                .workerId(workerId)
                .minutesWorked(totalMinutes)
                .build();
    }

    /**
     * Finds worker entries excluding group resource work (e.g., Wycinanie).
     * Used for speed index calculation - group resource work should not affect efficiency score.
     */
    private WorkerTimeDto findWorkerExcludingGroupResource(JobDto job, String workerId) {
        List<WorkerTimeDto> workerEntries = job.getWorkers().stream()
                .filter(w -> workerId.equals(w.getWorkerId()))
                .filter(w -> !w.isGroupResource())
                .toList();

        if (workerEntries.isEmpty()) {
            return null;
        }

        BigDecimal totalMinutes = workerEntries.stream()
                .map(WorkerTimeDto::getMinutesWorked)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return WorkerTimeDto.builder()
                .workerId(workerId)
                .resourceId(workerId)
                .minutesWorked(totalMinutes)
                .build();
    }

    private List<WorkerTimeDto> findWorkerEntries(JobDto job, String workerId) {
        return job.getWorkers().stream()
                .filter(w -> workerId.equals(w.getWorkerId()))
                .toList();
    }

    public List<JobDto> filterJobs(
            List<JobDto> jobs,
            LocalDate dateFrom,
            LocalDate dateTo,
            Set<String> selectedProducts,
            Set<String> excludedWorkers,
            boolean soloOnly
    ) {
        return jobs.stream()
                .map(job -> filterJobByWorkerDates(job, dateFrom, dateTo))
                .filter(job -> !job.getWorkers().isEmpty())
                .filter(job -> {
                    if (selectedProducts != null && !selectedProducts.isEmpty() && !selectedProducts.contains(job.getProductTypeId())) {
                        return false;
                    }
                    if (excludedWorkers != null && !excludedWorkers.isEmpty()) {
                        boolean hasExcluded = job.getWorkers().stream()
                                .anyMatch(w -> excludedWorkers.contains(w.getWorkerId()));
                        if (hasExcluded) {
                            return false;
                        }
                    }
                    if (soloOnly) {
                        long uniqueWorkers = job.getWorkers().stream()
                                .map(WorkerTimeDto::getWorkerId)
                                .distinct()
                                .count();
                        if (uniqueWorkers != 1) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    private JobDto filterJobByWorkerDates(JobDto job, LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null && dateTo == null) {
            return job;
        }

        if (job.getWorkers().isEmpty()) {
            return job;
        }

        List<WorkerTimeDto> filteredWorkers = job.getWorkers().stream()
                .filter(worker -> {
                    LocalDate workDate = worker.getWorkDate() != null ? worker.getWorkDate() : job.getDate();
                    if (workDate == null) {
                        return true;
                    }
                    if (dateFrom != null && workDate.isBefore(dateFrom)) {
                        return false;
                    }
                    if (dateTo != null && workDate.isAfter(dateTo)) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        if (filteredWorkers.isEmpty()) {
            return JobDto.builder()
                    .id(job.getId())
                    .numerZlecenia(job.getNumerZlecenia())
                    .date(job.getDate())
                    .productTypeId(job.getProductTypeId())
                    .quantity(BigDecimal.ZERO)
                    .workers(filteredWorkers)
                    .totalMinutes(BigDecimal.ZERO)
                    .build();
        }

        BigDecimal newTotalMinutes = filteredWorkers.stream()
                .map(WorkerTimeDto::getMinutesWorked)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return JobDto.builder()
                .id(job.getId())
                .numerZlecenia(job.getNumerZlecenia())
                .date(job.getDate())
                .productTypeId(job.getProductTypeId())
                .quantity(job.getQuantity())
                .workers(filteredWorkers)
                .totalMinutes(newTotalMinutes)
                .build();
    }
}