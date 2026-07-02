package org.example.bianalyticsservice.controller.grafik.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One assigned task in the grafik: a worker on an order for a planned window/duration. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrafikEntryDto {
    /** dbo.CtiZasobDok.CZS_ID — the primary key used to update hours. */
    private Integer czsId;
    private String workerName;
    private Integer resourceId;
    private Integer orderId;
    private String orderNumber;
    /** Contractor/customer name (odbiorca on the linked RO doc); null if the order has no linked doc. */
    private String contractorName;
    private String productCode;
    private BigDecimal quantity;
    /** CZN_Status: 0 draft, 1/2 in progress, 3 closed. */
    private Integer orderStatus;
    /** Assigned duration in hours (CZS_CzasPracy when CZS_JMCzasu = 2). */
    private BigDecimal plannedHours;
    /** Convenience: plannedHours expressed in whole minutes (15-min aligned). */
    private Integer plannedMinutes;
    private Integer timeUnit;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 0 = not finished (all plan rows), non-zero = finished. */
    private Integer finished;
}
