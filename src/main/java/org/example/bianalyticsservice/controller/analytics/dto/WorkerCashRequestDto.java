package org.example.bianalyticsservice.controller.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerCashRequestDto {
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private BigDecimal hourlyRate;
    private Set<String> selectedProducts;
    private Set<String> excludedWorkers;
    private Boolean ignoreInternalWork;
    private String minConfidence;
    private String confidenceFilter;
}
