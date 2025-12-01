package org.example.bianalyticsservice.controller.product.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    private String code;
    private String name;
    private String unitOfMeasure;
    private LocalDateTime purchaseDate;
    private BigDecimal quantity;
    private BigDecimal price;
}