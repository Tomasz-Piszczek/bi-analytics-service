package org.example.bianalyticsservice.controller.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditMaterialEntryDto {
    private String twrKod;
    private BigDecimal expectedIlosc;  // null = nie powinno być
    private BigDecimal actualIlosc;    // null = brakuje
    private boolean ok;
}
