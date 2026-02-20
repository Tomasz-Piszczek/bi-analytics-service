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
public class WorkerStatsDto {
    private String workerId;
    private String resourceId;
    private BigDecimal speedIndex;
    private BigDecimal presence;
    private BigDecimal production;
    private BigDecimal internalWork;
    private BigDecimal idle;
    private Integer jobCount;
    private List<DailyWorkerDetailDto> dailyDetails;
    private List<CappedDayDto> cappedDays;
}