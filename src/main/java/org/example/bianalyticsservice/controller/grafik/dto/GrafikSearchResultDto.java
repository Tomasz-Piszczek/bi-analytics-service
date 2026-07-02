package org.example.bianalyticsservice.controller.grafik.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * One search hit in the grafik: an order on a specific day. Groups all the
 * per-worker plan rows ({@code czsIds}) of that order/day so the frontend can
 * jump to and highlight the whole block set at once.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrafikSearchResultDto {
    /** Day the order is planned on (yyyy-MM-dd), the jump target. */
    private String date;
    private Integer orderId;
    private String orderNumber;
    /** Contractor/customer name (odbiorca on the linked RO doc); may be null. */
    private String contractorName;
    private String productCode;
    /** CZN_Status: 0 draft, 1/2 in progress, 3 closed. */
    private Integer orderStatus;
    private BigDecimal quantity;
    /** Earliest planned start that day, "HH:mm". */
    private String startTime;
    /** Distinct workers assigned that day, in first-seen order. */
    private List<String> workers;
    /** dbo.CtiZasobDok.CZS_ID of every block of this order on this day (to highlight). */
    private List<Integer> czsIds;
}
