package org.example.bianalyticsservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.example.bianalyticsservice.controller.analytics.dto.*;
import org.example.bianalyticsservice.controller.employee.dto.EmployeeHoursDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkerAnalyticsService {

    private final WorkerAnalyticsCacheService workerAnalyticsCacheService;
    private final ObjectMapper objectMapper;
    private final EmployeeService employeeService;
    private final WorkerStatsCalculator workerStatsCalculator;

    public WorkerAnalyticsResponseDto getWorkerAnalytics(WorkerAnalyticsRequestDto request) {
        List<Object[]> rawData = workerAnalyticsCacheService.getWorkerAnalytics();

        List<JobDto> allJobs = rawData.stream()
                .map(this::mapToJobDto)
                .collect(Collectors.toList());

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

        List<EmployeeHoursDto> employeeHours = filteredWorkerIds.isEmpty()
                ? Collections.emptyList()
                : employeeService.getAllEmployeesHours(new ArrayList<>(filteredWorkerIds));

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

    private LocalDate toLocalDate(Object obj) {
        if (obj instanceof Date d) {
            return d.toLocalDate();
        } else if (obj instanceof Timestamp ts) {
            return ts.toLocalDateTime().toLocalDate();
        }
        return null;
    }

    private JobDto mapToJobDto(Object[] row) {
        return JobDto.builder()
                .id((Integer) row[0])
                .numerZlecenia((String) row[1])
                .date(toLocalDate(row[2]))
                .productTypeId((String) row[3])
                .quantity(new BigDecimal(row[4].toString()))
                .workers(parseWorkersJson((String) row[5]))
                .totalMinutes(new BigDecimal(row[6].toString()))
                .rwElements(parseDocumentElementsJson((String) row[7]))
                .rwSuma(row[8] != null ? new BigDecimal(row[8].toString()) : null)
                .pwElements(parseDocumentElementsJson((String) row[9]))
                .pwSuma(row[10] != null ? new BigDecimal(row[10].toString()) : null)
                .build();
    }

    @SneakyThrows
    private List<WorkerTimeDto> parseWorkersJson(String workersJson) {
        if (workersJson == null || workersJson.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> workersList = objectMapper.readValue(
                workersJson,
                new TypeReference<>() {}
        );

        return workersList.stream()
                .map(map -> WorkerTimeDto.builder()
                        .workerId((String) map.get("workerId"))
                        .resourceId((String) map.get("resourceId"))
                        .workDate(map.get("workDate") != null ? LocalDate.parse(map.get("workDate").toString()) : null)
                        .minutesWorked(new BigDecimal(map.get("minutesWorked").toString()))
                        .build())
                .collect(Collectors.toList());
    }

    @SneakyThrows
    private List<DocumentElementDto> parseDocumentElementsJson(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> elementsList = objectMapper.readValue(
                json,
                new TypeReference<>() {}
        );

        return elementsList.stream()
                .map(map -> DocumentElementDto.builder()
                        .dokNumer((String) map.get("dokNumer"))
                        .twrKod((String) map.get("twrKod"))
                        .ilosc(map.get("ilosc") != null ? new BigDecimal(map.get("ilosc").toString()) : null)
                        .wartoscNetto(map.get("wartoscNetto") != null ? new BigDecimal(map.get("wartoscNetto").toString()) : null)
                        .build())
                .collect(Collectors.toList());
    }
}