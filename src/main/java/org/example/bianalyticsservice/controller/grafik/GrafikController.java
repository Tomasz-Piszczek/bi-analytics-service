package org.example.bianalyticsservice.controller.grafik;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.controller.grafik.dto.GrafikEntryDto;
import org.example.bianalyticsservice.controller.grafik.dto.GrafikResponseDto;
import org.example.bianalyticsservice.controller.grafik.dto.UpdateAssignmentRequestDto;
import org.example.bianalyticsservice.service.GrafikService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Grafik produkcji — per-worker daily production schedule read from dbo.CtiZasobDok.
 * Deletion of assignments is intentionally NOT exposed.
 */
@Slf4j
@RestController
@RequestMapping("/api/grafik")
@RequiredArgsConstructor
public class GrafikController {

    private final GrafikService grafikService;

    /**
     * Grafik for a window. Provide either {@code date} (single day) or
     * {@code from}/{@code to} (range). If nothing is given, defaults to today.
     */
    @GetMapping
    public ResponseEntity<GrafikResponseDto> getGrafik(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (date != null) {
            log.info("[getGrafik] day={}", date);
            return ResponseEntity.ok(grafikService.getGrafikForDay(date));
        }
        LocalDate f = from != null ? from : LocalDate.now();
        LocalDate t = to != null ? to : f;
        log.info("[getGrafik] from={} to={}", f, t);
        return ResponseEntity.ok(grafikService.getGrafik(f, t));
    }

    /** Move/resize one plan row's time window (both ends snapped to 15 min server-side). */
    @PutMapping("/{czsId}")
    public ResponseEntity<GrafikEntryDto> updateAssignment(
            @PathVariable Integer czsId,
            @RequestBody UpdateAssignmentRequestDto request) {
        log.info("[updateAssignment] czsId={} start={} end={}",
                czsId, request.getStartTime(), request.getEndTime());
        return ResponseEntity.ok(
                grafikService.updateAssignment(czsId, request.getStartTime(), request.getEndTime()));
    }
}
