package org.example.bianalyticsservice.controller.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerDailyJobEntryDto {
    private String numerZlecenia;
    private String productTypeId;
    private BigDecimal minutesWorked;
    private LocalDateTime timeFrom;
    private LocalDateTime timeTo;
}
