package org.example.bianalyticsservice.infrastructure.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeChangeEvent {
    private String eventId;
    private String eventType;
    private Long timestamp;
    private List<EmployeeData> employees;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployeeData {
        private Integer id;
        private String code;
    }
}