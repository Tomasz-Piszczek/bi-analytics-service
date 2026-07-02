package org.example.bianalyticsservice.controller.grafik.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A window in which a worker is available on the viewed day, "HH:mm"–"HH:mm". */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityIntervalDto {
    private String from;
    private String to;
}
