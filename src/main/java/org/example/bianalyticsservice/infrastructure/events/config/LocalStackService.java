package org.example.bianalyticsservice.infrastructure.events.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalStackService {

    private final SqsAsyncClient sqsAsyncClient;

    @Value("${sqs.launch-localstack:false}")
    private boolean launchLocalStack;

    @Value("${sqs.local-uri:http://localhost:4567}")
    private String localStackUri;

    private Process localStackProcess;

    public void setupLocalStack() {
        if (!launchLocalStack) {
            log.info("LocalStack setup skipped (launch-localstack=false)");
            return;
        }

        log.info("Starting LocalStack...");
        
        if (!isLocalStackRunning()) {
            startLocalStackContainer();
            waitForLocalStackToBeReady();
        } else {
            log.info("LocalStack is already running");
        }

        log.info("Setting up LocalStack SQS queues");
        createQueue("employeeChangeQueue");
        log.info("LocalStack setup completed");
    }

    public void shutdownLocalStack() {
        if (!launchLocalStack) {
            return;
        }
        
        if (localStackProcess != null && localStackProcess.isAlive()) {
            log.info("Stopping LocalStack...");
            try {
                ProcessBuilder stopBuilder = new ProcessBuilder("docker", "stop", "localstack-bi-analytics");
                stopBuilder.start().waitFor(30, TimeUnit.SECONDS);
                
                ProcessBuilder removeBuilder = new ProcessBuilder("docker", "rm", "localstack-bi-analytics");
                removeBuilder.start().waitFor(30, TimeUnit.SECONDS);
                
                log.info("LocalStack stopped successfully");
            } catch (Exception e) {
                log.warn("Error stopping LocalStack: {}", e.getMessage());
            }
        }
    }

    private boolean isLocalStackRunning() {
        try {
            URL url = new URL(localStackUri + "/_localstack/health");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            
            int responseCode = connection.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void startLocalStackContainer() {
        try {
            ProcessBuilder removeBuilder = new ProcessBuilder("docker", "rm", "-f", "localstack-bi-analytics");
            Process removeProcess = removeBuilder.start();
            removeProcess.waitFor(5, TimeUnit.SECONDS);


            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "run", "-d",
                "--name", "localstack-bi-analytics",
                "-p", "4567:4566",
                "-e", "SERVICES=sqs,sns",
                "-e", "DEBUG=1",
                "-e", "HOSTNAME_EXTERNAL=localhost",
                "localstack/localstack:latest"
            );

            localStackProcess = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(localStackProcess.getInputStream()));
            String containerId = reader.readLine();

            if (containerId != null) {
                log.info("LocalStack container started with ID: {}", containerId.substring(0, 12));
            }

        } catch (Exception e) {
            log.error("Failed to start LocalStack container: {}", e.getMessage());
            throw new RuntimeException("Failed to start LocalStack", e);
        }
    }

    private void waitForLocalStackToBeReady() {
        int maxAttempts = 30;
        int attempt = 0;
        
        while (attempt < maxAttempts && !isLocalStackRunning()) {
            try {
                Thread.sleep(1000);
                attempt++;
                log.debug("Waiting for LocalStack to be ready... attempt {}/{}", attempt, maxAttempts);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for LocalStack", e);
            }
        }
        
        if (!isLocalStackRunning()) {
            throw new RuntimeException("LocalStack failed to start within 30 seconds");
        }
        
        log.info("LocalStack is ready and responding");
    }

    private void createQueue(String queueName) {
        try {
            CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                    .queueName(queueName)
                    .attributes(Map.of(
                            QueueAttributeName.VISIBILITY_TIMEOUT, "30",
                            QueueAttributeName.MESSAGE_RETENTION_PERIOD, "1209600"
                    ))
                    .build();

            sqsAsyncClient.createQueue(createQueueRequest).join();
            log.info("Created SQS queue: {}", queueName);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("QueueAlreadyExists")) {
                log.debug("Queue {} already exists", queueName);
            } else {
                log.warn("Failed to create queue {}: {}", queueName, e.getMessage());
            }
        }
    }
}