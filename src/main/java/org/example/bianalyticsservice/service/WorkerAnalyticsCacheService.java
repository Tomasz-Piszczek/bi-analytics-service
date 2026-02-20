package org.example.bianalyticsservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.config.CacheConfig;
import org.example.bianalyticsservice.repository.WorkerAnalyticsRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerAnalyticsCacheService {

    private final WorkerAnalyticsRepository workerAnalyticsRepository;

    @Cacheable(value = CacheConfig.WORKER_ANALYTICS_CACHE, key = "'allJobs'")
    public List<Object[]> getWorkerAnalytics() {
        log.info("Cache MISS - fetching worker analytics from database...");
        long start = System.currentTimeMillis();
        List<Object[]> result = workerAnalyticsRepository.findWorkerAnalytics();
        long duration = System.currentTimeMillis() - start;
        log.info("Database query completed in {}ms, returned {} records", duration, result.size());
        return result;
    }

    @CacheEvict(value = CacheConfig.WORKER_ANALYTICS_CACHE, allEntries = true)
    public void evictCache() {
        log.info("Worker analytics cache evicted");
    }
}
