package org.example.bianalyticsservice.controller.grafik.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Details of a production order shown when a block is clicked in the ACTUAL view:
 * who actually worked on it (with time) and which materials were issued (RW docs).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrafikOrderDetailDto {
    private Integer orderId;
    private String orderNumber;
    private String contractorName;
    private String productCode;
    private BigDecimal quantity;
    private Integer status;
    private List<Worker> workers;
    private List<Material> materials;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Worker {
        private String workerName;
        /** Total actually-logged minutes on this order. */
        private Integer minutes;
        /** Earliest logged start and latest logged end on this order. */
        private LocalDateTime fromTime;
        private LocalDateTime toTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Material {
        private String twrKod;
        private BigDecimal ilosc;
        private BigDecimal wartoscNetto;
    }
}
