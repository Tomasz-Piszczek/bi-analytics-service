package org.example.bianalyticsservice.infrastructure.events.outgoing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.infrastructure.events.model.EmployeeChangeEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class SqsEventDispatcher {

    @Value("${sqs.url.write.employeeChangeQueue:}")
    private String employeeChangeQueueUrl;

    private final SqsEventSendingService sendingService;

    public void dispatchEmployeeChangeEvent(EmployeeChangeEvent event) {
        log.info("Dispatching employee change event with {} employees", 
                event.getEmployees() != null ? event.getEmployees().size() : 0);
        
        sendingService.sendEvent(event, employeeChangeQueueUrl);
    }
}