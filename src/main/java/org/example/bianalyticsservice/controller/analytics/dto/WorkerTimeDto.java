package org.example.bianalyticsservice.controller.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerTimeDto {
    private String workerId;
    private String resourceId;
    private LocalDate workDate;
    private BigDecimal minutesWorked;
    private List<SessionDto> sessions;

    /**
     * Speed index contribution percentage (Wpływ).
     * Shows what percentage of the worker's speed index calculation came from this specific job.
     * Always positive, all jobs for a worker sum to 100%.
     * Higher percentage = job had more weight in determining the worker's speed index.
     * Null = cannot be calculated (no benchmark available).
     */
    private BigDecimal speedIndexContributionPercentage;

    /**
     * Returns true if this work was done via a group resource (e.g., Wycinanie)
     * rather than directly under the worker's own name.
     */
    public boolean isGroupResource() {
        return resourceId != null && !resourceId.equals(workerId);
    }
}
