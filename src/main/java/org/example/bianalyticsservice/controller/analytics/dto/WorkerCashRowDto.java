package org.example.bianalyticsservice.controller.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerCashRowDto {
    private String workerId;
    private String resourceId;
    private BigDecimal totalMinutes;
    private BigDecimal totalHours;
    private int jobsTouched;
    private BigDecimal attributedRevenue;
    private BigDecimal attributedMaterialCost;
    private BigDecimal attributedLaborCost;
    private BigDecimal attributedProfit;
    private BigDecimal profitPerHour;
    private List<WorkerCashTopJobDto> topJobs;
    private List<WorkerCashTopJobDto> worstJobs;
}
