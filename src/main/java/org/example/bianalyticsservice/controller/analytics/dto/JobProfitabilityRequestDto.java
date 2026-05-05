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
public class JobProfitabilityRequestDto {
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private BigDecimal hourlyRate;
    private Set<String> selectedProducts;
    private Set<String> excludedWorkers;
    private Boolean ignoreInternalWork;
    /** Legacy filter: "HIGH" → only Z fakturą rows. Kept for backwards compat with old clients. */
    private String minConfidence;
    /** New 3-way filter: "ALL" (default), "WITH_INVOICE" (only HIGH), "NO_INVOICE" (only orphans). */
    private String confidenceFilter;
    private String sortBy;
}
