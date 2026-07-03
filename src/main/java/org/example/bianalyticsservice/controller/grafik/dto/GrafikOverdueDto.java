package org.example.bianalyticsservice.controller.grafik.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * One overdue (zaległe) order: planned to finish in the past — ALL its resources'
 * planned windows end before today — yet the order is not closed. Orders with any
 * resource still planned today or later are NOT overdue and are excluded.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrafikOverdueDto {
    private Integer orderId;
    private String orderNumber;
    private String contractorName;
    private String productCode;
    private BigDecimal quantity;
    /** CZN_Status: 0 open, 1/2 in progress (never 3 closed here). */
    private Integer orderStatus;
    /** Planned end = MAX(CZS_CzasEnd) over ALL resources of the order (yyyy-MM-dd). */
    private String plannedEnd;
    /** Whole days between plannedEnd and today (>= 1). */
    private Integer daysOverdue;
    /** Named workers (Pracownicy group) assigned to the order, in first-seen order. */
    private List<String> workers;
    /** dbo.CtiZasobDok.CZS_ID of the order's worker rows on plannedEnd (for the jump/highlight). */
    private List<Integer> czsIds;
}
