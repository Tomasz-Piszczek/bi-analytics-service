package org.example.bianalyticsservice.controller.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.controller.analytics.dto.WorkerAnalyticsRequestDto;
import org.example.bianalyticsservice.controller.analytics.dto.WorkerAnalyticsResponseDto;
import org.example.bianalyticsservice.service.WorkerAnalyticsCacheService;
import org.example.bianalyticsservice.service.WorkerAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final WorkerAnalyticsService workerAnalyticsService;
    private final WorkerAnalyticsCacheService workerAnalyticsCacheService;

    @PostMapping("/worker-analytics")
    public ResponseEntity<WorkerAnalyticsResponseDto> getWorkerAnalytics(
            @RequestBody WorkerAnalyticsRequestDto request) {
        log.info("[getWorkerAnalytics] Fetching worker analytics with filters");
        return ResponseEntity.ok(workerAnalyticsService.getWorkerAnalytics(request));
    }

    @PostMapping("/worker-analytics/refresh-cache")
    public ResponseEntity<String> refreshCache() {
        log.info("[refreshCache] Manually evicting worker analytics cache");
        workerAnalyticsCacheService.evictCache();
        return ResponseEntity.ok("Cache evicted successfully. Next request will fetch fresh data.");
    }
}
