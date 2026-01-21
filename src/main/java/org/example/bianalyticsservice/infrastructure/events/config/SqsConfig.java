package org.example.bianalyticsservice.infrastructure.events.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;

@Configuration
@RequiredArgsConstructor
@Slf4j
@Import(SqsClientProvider.class)
@DependsOn({"sqsAsyncClient", "snsAsyncClient"})
public class SqsConfig {

    private final LocalStackService localStackService;

    @PostConstruct
    public void setup() {
        localStackService.setupLocalStack();
    }

    @PreDestroy
    public void shutdown() {
        localStackService.shutdownLocalStack();
    }
}