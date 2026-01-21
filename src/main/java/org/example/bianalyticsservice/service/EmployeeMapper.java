package org.example.bianalyticsservice.service;

import org.example.bianalyticsservice.controller.employee.dto.EmployeeDto;
import org.example.bianalyticsservice.model.CtiZasob;
import org.springframework.stereotype.Component;

@Component
public class EmployeeMapper {

    public EmployeeDto mapToEmployeeDto(CtiZasob ctiZasob) {
        return EmployeeDto.builder()
                .id(ctiZasob.getId())
                .code(ctiZasob.getCode())
                .build();
    }
}