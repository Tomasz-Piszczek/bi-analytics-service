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
public class MaterialAuditResponseDto {
    private List<WorkerAuditDto> workers;
    private int totalOrders;
    private int totalCorrect;
    private int totalIncorrect;
}
