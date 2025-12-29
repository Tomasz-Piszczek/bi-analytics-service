package org.example.bianalyticsservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.controller.employee.dto.EmployeeDto;
import org.example.bianalyticsservice.model.CtiZasob;
import org.example.bianalyticsservice.repository.CtiZasobRepository;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {
    
    private final CtiZasobRepository ctiZasobRepository;
    private final EmployeeMapper employeeMapper;
    //todo fix work hours
    //todo createConfigClass
    public List<EmployeeDto> getEmployeesFromPracownicy() {
        List<CtiZasob> employees = ctiZasobRepository.findByGroupCode("Pracownicy");
        
        return employees.stream()
                .map(employeeMapper::mapToEmployeeDto)
                .collect(Collectors.toList());
    }
}