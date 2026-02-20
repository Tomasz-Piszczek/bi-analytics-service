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
public class DailyWorkerDetailDto {
    private LocalDate date;
    private BigDecimal productionHours;
    private BigDecimal internalHours;
    private BigDecimal idleHours;
    private BigDecimal attendanceHours;
    private boolean wasCapped;
}