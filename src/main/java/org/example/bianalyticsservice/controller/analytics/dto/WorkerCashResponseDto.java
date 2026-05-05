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
public class WorkerCashResponseDto {
    private List<WorkerCashRowDto> rows;
    private BigDecimal totalAttributedProfit;
    private BigDecimal totalAttributedRevenue;
    private int totalJobsAnalyzed;
}
