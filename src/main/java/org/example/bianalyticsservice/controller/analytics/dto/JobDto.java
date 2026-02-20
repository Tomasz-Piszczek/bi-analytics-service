package org.example.bianalyticsservice.controller.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDto {
    private Integer id;
    private String numerZlecenia;
    private LocalDate date;
    private String productTypeId;
    private BigDecimal quantity;
    private List<WorkerTimeDto> workers;
    private BigDecimal totalMinutes;

    private List<DocumentElementDto> rwElements;
    private BigDecimal rwSuma;
    private List<DocumentElementDto> pwElements;
    private BigDecimal pwSuma;
}
