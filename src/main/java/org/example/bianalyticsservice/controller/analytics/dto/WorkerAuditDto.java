package org.example.bianalyticsservice.controller.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerAuditDto {
    private String workerId;
    private List<AuditOrderDto> correctOrders;
    private List<AuditOrderDto> incorrectOrders;
    private int totalOrders;
    private int correctCount;
    private int incorrectCount;
}
