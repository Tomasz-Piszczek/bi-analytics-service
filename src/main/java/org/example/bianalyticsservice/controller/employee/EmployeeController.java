package org.example.bianalyticsservice.controller.employee;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.controller.employee.dto.EmployeeHoursDto;
import org.example.bianalyticsservice.service.EmployeeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @PostMapping("/hours")
    public ResponseEntity<List<EmployeeHoursDto>> getEmployeeHours(
            @RequestBody List<String> employeeNames,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        log.info("[getEmployeeHours] Getting hours for {} employees, year: {}, month: {}",
                employeeNames.size(), year, month);

        return ResponseEntity.ok(employeeService.getEmployeesHours(employeeNames, year, month));
    }
}
