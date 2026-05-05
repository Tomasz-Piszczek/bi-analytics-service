package org.example.bianalyticsservice.controller.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobProfitabilityWorkerDto {
    private String workerId;
    private String resourceId;
    private BigDecimal minutes;
    private BigDecimal hours;
    private BigDecimal laborCost;
    private BigDecimal shareOfProfit;
    private BigDecimal sharePct;
}
