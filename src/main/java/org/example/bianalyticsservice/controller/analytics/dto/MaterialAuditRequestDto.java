package org.example.bianalyticsservice.controller.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialAuditRequestDto {
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private BigDecimal offsetPercent;  // np. 10.0 = 10%
    private BigDecimal offsetNumber;   // np. 7.0
}
