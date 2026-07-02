package org.example.bianalyticsservice.controller.grafik.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** All of one worker's assignments within the requested grafik window. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrafikWorkerDto {
    private String workerName;
    private BigDecimal totalHours;
    private int entryCount;
    private List<GrafikEntryDto> entries;
    /** Availability windows on the viewed day; empty = unavailable all day. */
    private List<AvailabilityIntervalDto> available;
}
