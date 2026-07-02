package org.example.bianalyticsservice.controller.grafik;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.controller.grafik.dto.GrafikEntryDto;
import org.example.bianalyticsservice.controller.grafik.dto.GrafikOrderDetailDto;
import org.example.bianalyticsservice.controller.grafik.dto.GrafikResponseDto;
import org.example.bianalyticsservice.controller.grafik.dto.GrafikSearchResultDto;
import org.example.bianalyticsservice.controller.grafik.dto.UpdateAssignmentRequestDto;
import org.example.bianalyticsservice.service.GrafikService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

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

    /**
     * ACTUAL (rzeczywisty) grafik — how orders were really executed, from logged
     * work intervals. Same shape/params as the planned {@link #getGrafik}.
     */
    @GetMapping("/actual")
    public ResponseEntity<GrafikResponseDto> getGrafikActual(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = date != null ? date : (from != null ? from : LocalDate.now());
        LocalDate t = date != null ? date : (to != null ? to : f);
        log.info("[getGrafikActual] from={} to={}", f, t);
        return ResponseEntity.ok(grafikService.getGrafikActual(f, t));
    }

    /** Order detail (actual view): who worked on it + materials issued. */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<GrafikOrderDetailDto> getOrderDetail(@PathVariable Integer orderId) {
        log.info("[getOrderDetail] orderId={}", orderId);
        return ResponseEntity.ok(grafikService.getOrderDetail(orderId));
    }

    /**
     * Search the grafik by contractor name or order number. Returns matching
     * order/day groups ranked by name-match then furthest-future plan date.
     */
    @GetMapping("/search")
    public ResponseEntity<List<GrafikSearchResultDto>> search(@RequestParam("q") String q) {
        log.info("[searchGrafik] q={}", q);
        return ResponseEntity.ok(grafikService.search(q));
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
