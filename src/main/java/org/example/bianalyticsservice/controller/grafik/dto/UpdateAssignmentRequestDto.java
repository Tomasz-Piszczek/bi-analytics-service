package org.example.bianalyticsservice.controller.grafik.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request body for moving/resizing a plan row on the grafik timeline.
 * Both ends are snapped to the nearest 15 minutes server-side; the assigned
 * duration ({@code CZS_CzasPracy}) is recomputed from the window.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAssignmentRequestDto {
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;
}
