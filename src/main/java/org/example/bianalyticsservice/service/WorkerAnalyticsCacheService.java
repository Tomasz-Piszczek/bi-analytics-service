package org.example.bianalyticsservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.config.CacheConfig;
import org.example.bianalyticsservice.controller.analytics.dto.DocumentElementDto;
import org.example.bianalyticsservice.controller.analytics.dto.JobDto;
import org.example.bianalyticsservice.controller.analytics.dto.WorkerTimeDto;
import org.example.bianalyticsservice.controller.employee.dto.DailyHoursDto;
import org.example.bianalyticsservice.controller.employee.dto.EmployeeHoursDto;
import org.example.bianalyticsservice.repository.CtiProdukcjaPanelRCPRepository;
import org.example.bianalyticsservice.repository.WorkerAnalyticsRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerAnalyticsCacheService {

    private final WorkerAnalyticsRepository workerAnalyticsRepository;
    private final CtiProdukcjaPanelRCPRepository ctiProdukcjaPanelRCPRepository;
    private final ObjectMapper objectMapper;

    /**
     * Returns cached List<JobDto> - already mapped from database.
     * This caches both the DB query AND the JSON parsing.
     */
    @Cacheable(value = CacheConfig.WORKER_ANALYTICS_CACHE, key = "'allJobsMapped'")
    public List<JobDto> getAllJobs() {
        log.info("Cache MISS - fetching and mapping worker analytics from database...");
        long start = System.currentTimeMillis();

        List<Object[]> rawData = workerAnalyticsRepository.findWorkerAnalytics();
        long dbTime = System.currentTimeMillis() - start;
        log.info("Database query completed in {}ms, returned {} records", dbTime, rawData.size());

        long mapStart = System.currentTimeMillis();
        List<JobDto> result = rawData.stream()
                .map(this::mapToJobDto)
                .collect(Collectors.toList());
        long mapTime = System.currentTimeMillis() - mapStart;
        log.info("Mapping completed in {}ms", mapTime);

        return result;
    }

    /**
     * Returns cached Map of employeeName -> EmployeeHoursDto.
     * All employees' hours fetched in ONE query and cached.
     */
    @Cacheable(value = CacheConfig.EMPLOYEE_HOURS_CACHE, key = "'allEmployeeHours'")
    public Map<String, EmployeeHoursDto> getAllEmployeeHoursMap() {
        log.info("Cache MISS - fetching all employee hours from database...");
        long start = System.currentTimeMillis();

        List<Object[]> rawData = ctiProdukcjaPanelRCPRepository.getAllEmployeesDailyHours();
        long dbTime = System.currentTimeMillis() - start;
        log.info("Employee hours query completed in {}ms, returned {} records", dbTime, rawData.size());

        // Group by employee name
        Map<String, List<DailyHoursDto>> dailyHoursByEmployee = new HashMap<>();
        for (Object[] row : rawData) {
            String employeeName = (String) row[0];
            DailyHoursDto dailyHours = DailyHoursDto.builder()
                    .date(((Date) row[1]).toLocalDate())
                    .hours((BigDecimal) row[2])
                    .startTime(row[3] != null ? ((Timestamp) row[3]).toLocalDateTime() : null)
                    .endTime(row[4] != null ? ((Timestamp) row[4]).toLocalDateTime() : null)
                    .build();
            dailyHoursByEmployee.computeIfAbsent(employeeName, k -> new ArrayList<>()).add(dailyHours);
        }

        // Convert to EmployeeHoursDto map
        Map<String, EmployeeHoursDto> result = new HashMap<>();
        for (Map.Entry<String, List<DailyHoursDto>> entry : dailyHoursByEmployee.entrySet()) {
            String employeeName = entry.getKey();
            List<DailyHoursDto> dailyHours = entry.getValue();
            BigDecimal totalHours = dailyHours.stream()
                    .map(DailyHoursDto::getHours)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            result.put(employeeName, EmployeeHoursDto.builder()
                    .employeeName(employeeName)
                    .year(null)
                    .month(null)
                    .hours(totalHours)
                    .dailyHours(dailyHours)
                    .build());
        }

        log.info("Mapped {} employees' hours data", result.size());
        return result;
    }

    @Caching(evict = {
            @CacheEvict(value = CacheConfig.WORKER_ANALYTICS_CACHE, allEntries = true),
            @CacheEvict(value = CacheConfig.EMPLOYEE_HOURS_CACHE, allEntries = true)
    })
    public void evictCache() {
        log.info("All analytics caches evicted");
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
