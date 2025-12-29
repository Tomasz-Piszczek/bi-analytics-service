package org.example.bianalyticsservice.controller.employee.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmployeeDto {
    private Integer id;
    private String code;
    private Integer departmentId;
}