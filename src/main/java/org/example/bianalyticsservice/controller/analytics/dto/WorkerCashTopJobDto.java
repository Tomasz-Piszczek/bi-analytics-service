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
public class WorkerCashTopJobDto {
    private String numerZlecenia;
    private String productTypeId;
    private String invoiceNumber;
    private BigDecimal myMinutes;
    private BigDecimal myShareOfProfit;
}
