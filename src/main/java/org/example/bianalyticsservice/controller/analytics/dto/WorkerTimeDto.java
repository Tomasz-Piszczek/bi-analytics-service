package org.example.bianalyticsservice.controller.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerTimeDto {
    private String workerId;
    private String resourceId;
    private LocalDate workDate;
    private BigDecimal minutesWorked;

    /**
     * Returns true if this work was done via a group resource (e.g., Wycinanie)
     * rather than directly under the worker's own name.
     */
    public boolean isGroupResource() {
        return resourceId != null && !resourceId.equals(workerId);
    }
}
