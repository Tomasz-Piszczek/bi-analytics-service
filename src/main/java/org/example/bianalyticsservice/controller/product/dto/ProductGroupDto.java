package org.example.bianalyticsservice.controller.product.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductGroupDto {
    private Integer id;
    private String code;
    private String name;
}