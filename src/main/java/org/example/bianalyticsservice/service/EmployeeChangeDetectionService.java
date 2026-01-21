package org.example.bianalyticsservice.service;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.controller.employee.dto.EmployeeDto;
import org.example.bianalyticsservice.infrastructure.events.model.EmployeeChangeEvent;
import org.example.bianalyticsservice.infrastructure.events.outgoing.SqsEventDispatcher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeChangeDetectionService {

    private final EmployeeService employeeService;
    private final SqsEventDispatcher sqsEventDispatcher;
    private final String EMPLOYEE_DATA_CHANGED = "EMPLOYEE_DATA_CHANGED";
    
    private volatile String lastKnownHash;

    @Scheduled(fixedDelay = 15000)
    public void detectEmployeeChanges() {
        List<EmployeeDto> currentEmployees = employeeService.getEmployeesFromPracownicy();
        String currentHash = calculateHash(currentEmployees);

        if (lastKnownHash == null) {
            lastKnownHash = currentHash;
            log.info("Initial employee data hash calculated: {}", currentHash);
            return;
        }

        if (!currentHash.equals(lastKnownHash)) {
            log.info("Employee data change detected. Previous hash: {}, Current hash: {}",
                    lastKnownHash, currentHash);

            EmployeeChangeEvent event = buildEmployeeChangeEvent(currentEmployees);
            sqsEventDispatcher.dispatchEmployeeChangeEvent(event);

            lastKnownHash = currentHash;
        }
    }
    @SneakyThrows
    private String calculateHash(List<EmployeeDto> employees) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            String concatenatedData = employees.stream()
                    .sorted(Comparator.comparingInt(EmployeeDto::getId))
                    .map(emp -> String.format("%d:%s:%d",
                            emp.getId(), 
                            emp.getCode(), 
                            emp.getDepartmentId()))
                    .collect(Collectors.joining("|"));
            
            byte[] hashBytes = digest.digest(concatenatedData.getBytes());
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
    }

    private EmployeeChangeEvent buildEmployeeChangeEvent(List<EmployeeDto> employees) {
        List<EmployeeChangeEvent.EmployeeData> employeeData = employees.stream()
                .map(emp -> EmployeeChangeEvent.EmployeeData.builder()
                        .id(emp.getId())
                        .code(emp.getCode())
                        .build())
                .collect(Collectors.toList());

        return EmployeeChangeEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EMPLOYEE_DATA_CHANGED)
                .timestamp(Instant.now().toEpochMilli())
                .employees(employeeData)
                .build();
    }
}