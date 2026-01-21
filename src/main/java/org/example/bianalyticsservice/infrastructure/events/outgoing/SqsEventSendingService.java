package org.example.bianalyticsservice.infrastructure.events.outgoing;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class SqsEventSendingService {

    private final SqsAsyncClient sqsAsyncClient;
    private final ObjectMapper objectMapper;

    public CompletableFuture<Void> sendEvent(Object event, String queueUrl) {
        try {
            String messageBody = objectMapper.writeValueAsString(event);
            
            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build();

            return sqsAsyncClient.sendMessage(sendMessageRequest)
                    .thenRun(() -> log.info("Event sent to queue {}: {}", queueUrl, event.getClass().getSimpleName()))
                    .exceptionally(throwable -> {
                        log.error("Failed to send event to queue {}: {}", queueUrl, throwable.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.error("Failed to serialize event: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
}