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
public class JobProfitabilityRowDto {
    private Integer cznId;
    private String numerZlecenia;
    private String productTypeId;
    private BigDecimal orderQuantity;
    private LocalDate orderDate;

    private String invoiceNumber;
    private LocalDate invoiceDate;
    private String roNumer;
    private Integer coBundledZpCount;
    private String revenueAttributionConfidence;

    private BigDecimal revenue;
    private BigDecimal materialCost;
    private BigDecimal laborMinutes;
    private BigDecimal laborHours;
    private BigDecimal laborCost;
    private BigDecimal profit;
    private BigDecimal marginPct;

    private List<JobProfitabilityWorkerDto> workers;
    private List<DocumentElementDto> rwElements;

    private boolean hasMaterialCost;
    private boolean hasLaborTime;
    private boolean fskorAdjustmentApplied;
}
