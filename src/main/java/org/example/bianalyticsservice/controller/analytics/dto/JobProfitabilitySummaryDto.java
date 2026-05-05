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
public class JobProfitabilitySummaryDto {
    private int totalJobs;
    /** ZPs with an FS line that exact-matches the full product code. */
    private int withInvoiceJobs;
    /** ZPs that are linked to an FS but have no matching line on it (revenue=0). */
    private int noInvoiceJobs;
    /** ZPs with no FS link in the requested date window at all (filtered out before rows are built). */
    private int hiddenUninvoicedJobs;
    private BigDecimal totalRevenue;
    private BigDecimal totalMaterialCost;
    private BigDecimal totalLaborCost;
    private BigDecimal totalProfit;
    private BigDecimal avgMarginPct;
    private List<JobProfitabilityRowDto> top5;
    private List<JobProfitabilityRowDto> bottom5;
}
