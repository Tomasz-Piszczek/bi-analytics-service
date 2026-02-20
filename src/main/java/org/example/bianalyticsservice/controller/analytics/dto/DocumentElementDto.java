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
public class DocumentElementDto {
    private String dokNumer;
    private String twrKod;
    private BigDecimal ilosc;
    private BigDecimal wartoscNetto;
}
