package org.example.bianalyticsservice.controller.employee.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyHoursDto {
    private LocalDate date;
    private BigDecimal hours;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
