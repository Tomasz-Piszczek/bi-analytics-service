package org.example.bianalyticsservice.controller.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductGroupDto {
    private Integer id;
    private String code;
    private String name;
    private Integer parentId;
    private Integer level;
    private String path;
}