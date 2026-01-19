package org.example.bianalyticsservice.controller.employee.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeHoursDto {
    private String employeeName;
    private Integer year;
    private Integer month;
    private BigDecimal hours;
}
