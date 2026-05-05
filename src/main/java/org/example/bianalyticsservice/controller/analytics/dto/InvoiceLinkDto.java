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
public class InvoiceLinkDto {
    private Integer cznId;
    private Integer roId;
    private String roNumer;
    private Integer fsId;
    private String fsNumer;
    private LocalDate fsDate;
    private Integer coBundledZpCount;
    private List<InvoiceLineDto> fsLines;
    private BigDecimal fsKorrectionNet;
    private String jobProductCodeFull;
}
