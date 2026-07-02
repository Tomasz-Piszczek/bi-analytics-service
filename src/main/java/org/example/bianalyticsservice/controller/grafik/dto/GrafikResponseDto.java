package org.example.bianalyticsservice.controller.grafik.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The grafik for a date window, grouped by worker. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrafikResponseDto {
    private LocalDate from;
    private LocalDate to;
    private int workerCount;
    private int orderCount;
    private BigDecimal totalHours;
    private List<GrafikWorkerDto> workers;
}
