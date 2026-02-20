package org.example.bianalyticsservice.controller.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerAnalyticsRequestDto {
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Set<String> selectedProducts;
    private Set<String> excludedWorkers;
    private Boolean soloOnly;
    private Boolean ignoreInternalWork;
}
