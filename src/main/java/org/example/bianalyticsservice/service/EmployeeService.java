package org.example.bianalyticsservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.controller.employee.dto.DailyHoursDto;
import org.example.bianalyticsservice.controller.employee.dto.EmployeeDto;
import org.example.bianalyticsservice.controller.employee.dto.EmployeeHoursDto;
import org.example.bianalyticsservice.model.CtiZasob;
import org.example.bianalyticsservice.repository.CtiZasobRepository;
import org.example.bianalyticsservice.repository.CtiProdukcjaPanelRCPRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {
    //todo createConfigClass to map PRACOWNICy

    private final CtiZasobRepository ctiZasobRepository;
    private final CtiProdukcjaPanelRCPRepository ctiProdukcjaPanelRCPRepository;
    private final EmployeeMapper employeeMapper;

    public List<EmployeeDto> getEmployeesFromPracownicy() {
        List<CtiZasob> employees = ctiZasobRepository.findByGroupCode("Pracownicy");

        return employees.stream()
                .map(employeeMapper::mapToEmployeeDto)
                .collect(Collectors.toList());
    }

    public EmployeeHoursDto getEmployeeHours(String employeeName, Integer year, Integer month) {

        BigDecimal hours = ctiProdukcjaPanelRCPRepository.getHoursWorkedByEmployeeAndYearAndMonth(
                employeeName, year, month
        );

        BigDecimal hoursWorked = hours != null ? hours : BigDecimal.ZERO;

        List<Object[]> dailyHoursData = ctiProdukcjaPanelRCPRepository.getDailyHoursWorkedByEmployeeAndYearAndMonth(
                employeeName, year, month
        );

        List<DailyHoursDto> dailyHours = dailyHoursData.stream()
                .map(row -> DailyHoursDto.builder()
                        .date(((Date) row[0]).toLocalDate())
                        .hours((BigDecimal) row[1])
                        .build())
                .collect(Collectors.toList());

        return EmployeeHoursDto.builder()
                .employeeName(employeeName)
                .year(year)
                .month(month)
                .hours(hoursWorked)
                .dailyHours(dailyHours)
                .build();
    }

    public List<EmployeeHoursDto> getEmployeesHours(List<String> employeeNames, Integer year, Integer month) {
        return employeeNames.stream()
                .map(employeeName -> getEmployeeHours(employeeName, year, month))
                .collect(Collectors.toList());
    }
}