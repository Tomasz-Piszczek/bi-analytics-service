package org.example.bianalyticsservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    public static final String WORKER_ANALYTICS_CACHE = "workerAnalyticsCache";
    public static final String EMPLOYEE_HOURS_CACHE = "employeeHoursCache";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                WORKER_ANALYTICS_CACHE,
                EMPLOYEE_HOURS_CACHE
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)  // Cache expires after 30 minutes
                .maximumSize(10)                          // Max 10 entries
                .recordStats());                          // Enable stats for monitoring
        return cacheManager;
    }
}
