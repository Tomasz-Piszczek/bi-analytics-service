package org.example.bianalyticsservice.service;

import org.example.bianalyticsservice.service.GrafikService.AvailInterval;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the CzasPracy (work-hours) rules used when a grafik block is
 * moved/resized:
 *  - single-day: full window (end - start), regardless of availability;
 *  - multi-day: only the parts of the window inside the worker's availability
 *    (non-available gaps, e.g. overnight, are excluded);
 *  - value = effectiveMinutes / 60 / ceil(qty / divisor).
 * Pure functions — no DB / Spring context.
 */
class GrafikServiceCzasPracyTest {

    private static LocalDateTime dt(String iso) {
        return LocalDateTime.parse(iso);
    }

    // ---------- single-day: full window, availability ignored ----------

    @Test
    void singleDay_countsFullWindow_evenWhenWorkerNotAvailable() {
        // pracownika nie ma cały dzień, dajemy 09:00–14:00 → liczymy 5h
        long min = GrafikService.effectiveMinutes(
                dt("2026-04-28T09:00"), dt("2026-04-28T14:00"), List.of());
        assertEquals(5 * 60, min);
    }

    @Test
    void singleDay_ignoresAvailabilityLimits() {
        // dostępny tylko 07:00–10:00, ale okno 09:00–14:00 → i tak pełne 5h (jednodniowe)
        long min = GrafikService.effectiveMinutes(
                dt("2026-04-28T09:00"), dt("2026-04-28T14:00"),
                List.of(new AvailInterval(dt("2026-04-28T07:00"), dt("2026-04-28T10:00"))));
        assertEquals(5 * 60, min);
    }

    @Test
    void zeroLength_isZero() {
        assertEquals(0, GrafikService.effectiveMinutes(
                dt("2026-04-28T07:00"), dt("2026-04-28T07:00"), List.of()));
    }

    // ---------- multi-day: only within availability, gaps excluded ----------

    @Test
    void multiDay_excludesOvernightGap() {
        // 23.04 07:00 → 24.04 13:00; dostępny 07:00–15:00 oba dni → 8h + 6h = 14h
        long min = GrafikService.effectiveMinutes(
                dt("2026-04-23T07:00"), dt("2026-04-24T13:00"),
                List.of(
                        new AvailInterval(dt("2026-04-23T07:00"), dt("2026-04-23T15:00")),
                        new AvailInterval(dt("2026-04-24T07:00"), dt("2026-04-24T15:00"))));
        assertEquals(14 * 60, min);
    }

    @Test
    void multiDay_dayWithoutAvailabilityContributesZero() {
        // Maria: 23.04 brak dostępności, 24.04 07:00–17:00 → tylko 24.04 07:00–13:00 = 6h
        long min = GrafikService.effectiveMinutes(
                dt("2026-04-23T07:00"), dt("2026-04-24T13:00"),
                List.of(new AvailInterval(dt("2026-04-24T07:00"), dt("2026-04-24T17:00"))));
        assertEquals(6 * 60, min);
    }

    @Test
    void multiDay_clipsWindowStartInsideAvailability() {
        // start 23.04 12:00 (dostępność od 07:00) → liczymy od 12:00; 12:00–15:00 = 3h; + 24.04 07:00–09:00 = 2h → 5h
        long min = GrafikService.effectiveMinutes(
                dt("2026-04-23T12:00"), dt("2026-04-24T09:00"),
                List.of(
                        new AvailInterval(dt("2026-04-23T07:00"), dt("2026-04-23T15:00")),
                        new AvailInterval(dt("2026-04-24T07:00"), dt("2026-04-24T17:00"))));
        assertEquals(5 * 60, min);
    }

    // ---------- czasPracyHours: /60 and /ceil(qty/divisor) ----------

    @Test
    void czasPracy_qty1_divisor1() {
        assertEquals(new BigDecimal("6.0000"), GrafikService.czasPracyHours(360, BigDecimal.ONE, BigDecimal.ONE));
    }

    @Test
    void czasPracy_dividesByCeilQtyOverDivisor() {
        // 720 min = 12h, qty=2, dzielnik=1 → mult=2 → 6h
        assertEquals(new BigDecimal("6.0000"), GrafikService.czasPracyHours(720, new BigDecimal("2"), BigDecimal.ONE));
    }

    @Test
    void czasPracy_ceilRoundsUp() {
        // qty=5, dzielnik=2 → ceil(2.5)=3; 900 min = 15h /3 = 5h
        assertEquals(new BigDecimal("5.0000"), GrafikService.czasPracyHours(900, new BigDecimal("5"), new BigDecimal("2")));
    }

    @Test
    void czasPracy_nullOrZeroDivisor_multIsOne() {
        assertEquals(new BigDecimal("14.0000"), GrafikService.czasPracyHours(840, BigDecimal.ONE, null));
        assertEquals(new BigDecimal("14.0000"), GrafikService.czasPracyHours(840, BigDecimal.ONE, BigDecimal.ZERO));
    }
}
