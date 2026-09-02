package org.example.bianalyticsservice.infrastructure.events.outgoing;

import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.infrastructure.events.model.EmployeeChangeEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
@Slf4j
@ConditionalOnProperty(name = "employee-sync.enabled", havingValue = "true")
public class EmployeeEventDispatcher {

    private static final String INTERNAL_TOKEN_HEADER = "X-GearTrack-Internal-Token";

    private final RestTemplate restTemplate;

    @Value("${employee-sync.base-url}")
    private String gearTrackApiBaseUrl;

    @Value("${employee-sync.secret}")
    private String internalSecret;

    public EmployeeEventDispatcher(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void dispatchEmployeeChangeEvent(EmployeeChangeEvent event) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(INTERNAL_TOKEN_HEADER, internalSecret);

        String endpoint = stripTrailingSlash(gearTrackApiBaseUrl) + "/internal/employee-sync";
        restTemplate.postForEntity(endpoint, new HttpEntity<>(event, headers), Void.class);

        log.info("Synchronized {} employees directly with GearTrack API",
                event.getEmployees() != null ? event.getEmployees().size() : 0);
    }

    private String stripTrailingSlash(String value) {
        if (value != null && value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
