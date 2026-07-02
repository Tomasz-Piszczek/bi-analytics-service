package org.example.bianalyticsservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.controller.grafik.dto.AvailabilityIntervalDto;
import org.example.bianalyticsservice.controller.grafik.dto.GrafikEntryDto;
import org.example.bianalyticsservice.controller.grafik.dto.GrafikOrderDetailDto;
import org.example.bianalyticsservice.controller.grafik.dto.GrafikResponseDto;
import org.example.bianalyticsservice.controller.grafik.dto.GrafikSearchResultDto;
import org.example.bianalyticsservice.controller.grafik.dto.GrafikWorkerDto;
import org.example.bianalyticsservice.model.CtiZasobDok;
import org.example.bianalyticsservice.repository.CtiZasobDokRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrafikService {

    /** Editing granularity: 15 minutes = 0.25 h. */
    private static final BigDecimal QUARTER = new BigDecimal("0.25");
    private static final BigDecimal MAX_HOURS = new BigDecimal("24");
    private static final BigDecimal SIXTY = new BigDecimal("60");
    /** Snap step for the timeline, in minutes. */
    private static final int SNAP_MINUTES = 15;

    private final CtiZasobDokRepository repository;

    /** Grafik for a single day. */
    public GrafikResponseDto getGrafikForDay(LocalDate date) {
        return getGrafik(date, date);
    }

    /** PLANNED grafik for a date window [from, to], grouped by worker. */
    public GrafikResponseDto getGrafik(LocalDate from, LocalDate to) {
        return buildResponse(repository.findGrafik(from, to), from, to);
    }

    /** ACTUAL (rzeczywisty) grafik — real logged work from CtiZlecenieZasob. */
    public GrafikResponseDto getGrafikActual(LocalDate from, LocalDate to) {
        return buildResponse(repository.findGrafikActual(from, to), from, to);
    }

    /** Order detail for the actual-view popover: who worked (with minutes) + materials issued. */
    public GrafikOrderDetailDto getOrderDetail(Integer orderId) {
        GrafikOrderDetailDto.GrafikOrderDetailDtoBuilder b = GrafikOrderDetailDto.builder().orderId(orderId);
        List<Object[]> hdr = repository.findOrderHeader(orderId);
        if (!hdr.isEmpty()) {
            Object[] h = hdr.get(0);
            b.orderNumber(str(h[0]))
             .productCode(str(h[1]))
             .quantity(toBigDecimal(h[2]))
             .status(toInt(h[3]))
             .contractorName(str(h[4]));
        }
        List<GrafikOrderDetailDto.Worker> workers = new ArrayList<>();
        for (Object[] r : repository.findOrderWorkers(orderId)) {
            BigDecimal min = toBigDecimal(r[1]);
            workers.add(GrafikOrderDetailDto.Worker.builder()
                    .workerName(str(r[0]))
                    .minutes(min == null ? 0 : min.setScale(0, RoundingMode.HALF_UP).intValue())
                    .fromTime(toLocalDateTime(r[2]))
                    .toTime(toLocalDateTime(r[3]))
                    .build());
        }
        List<GrafikOrderDetailDto.Material> materials = new ArrayList<>();
        for (Object[] r : repository.findOrderMaterials(orderId)) {
            materials.add(GrafikOrderDetailDto.Material.builder()
                    .twrKod(str(r[0]))
                    .ilosc(toBigDecimal(r[1]))
                    .wartoscNetto(toBigDecimal(r[2]))
                    .build());
        }
        return b.workers(workers).materials(materials).build();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString().trim();
    }

    /** Group raw rows (planned or actual, same column shape) by worker + attach availability. */
    private GrafikResponseDto buildResponse(List<Object[]> rows, LocalDate from, LocalDate to) {
        Map<String, List<AvailabilityIntervalDto>> availByWorker = loadAvailability(from);

        Map<String, List<GrafikEntryDto>> byWorker = new LinkedHashMap<>();
        for (Object[] r : rows) {
            GrafikEntryDto e = mapRow(r);
            byWorker.computeIfAbsent(e.getWorkerName(), k -> new ArrayList<>()).add(e);
        }

        List<GrafikWorkerDto> workers = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        int orderCount = 0;
        for (Map.Entry<String, List<GrafikEntryDto>> ent : byWorker.entrySet()) {
            List<GrafikEntryDto> entries = ent.getValue();
            BigDecimal total = entries.stream()
                    .map(GrafikEntryDto::getPlannedHours)
                    .filter(h -> h != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            workers.add(GrafikWorkerDto.builder()
                    .workerName(ent.getKey())
                    .totalHours(total)
                    .entryCount(entries.size())
                    .entries(entries)
                    .available(availByWorker.getOrDefault(ent.getKey().trim(), List.of()))
                    .build());
            grandTotal = grandTotal.add(total);
            orderCount += entries.size();
        }
        workers.sort(Comparator.comparing(GrafikWorkerDto::getWorkerName, String.CASE_INSENSITIVE_ORDER));

        return GrafikResponseDto.builder()
                .from(from)
                .to(to)
                .workerCount(workers.size())
                .orderCount(orderCount)
                .totalHours(grandTotal)
                .workers(workers)
                .build();
    }

    /** Max distinct order/day groups returned by a search. */
    private static final int SEARCH_LIMIT = 20;

    /**
     * Search the grafik by order number or contractor name. Rows are grouped by
     * (order, day) so each result is a single jump target listing its workers,
     * preserving the repository ranking (prefix match first, then furthest-future).
     * Returns empty for blank/too-short queries.
     */
    public List<GrafikSearchResultDto> search(String q) {
        if (q == null || q.trim().length() < 2) {
            return List.of();
        }
        String term = escapeLike(q.trim());
        List<Object[]> rows = repository.searchGrafik("%" + term + "%", term + "%");

        Map<String, GrafikSearchResultDto> byGroup = new LinkedHashMap<>();
        for (Object[] r : rows) {
            GrafikEntryDto e = mapRow(r);
            if (e.getStartTime() == null) {
                continue;
            }
            String day = e.getStartTime().toLocalDate().toString();
            String key = e.getOrderNumber() + "|" + day;
            GrafikSearchResultDto g = byGroup.get(key);
            if (g == null) {
                if (byGroup.size() >= SEARCH_LIMIT) {
                    continue;
                }
                g = GrafikSearchResultDto.builder()
                        .date(day)
                        .orderId(e.getOrderId())
                        .orderNumber(e.getOrderNumber())
                        .contractorName(e.getContractorName())
                        .productCode(e.getProductCode())
                        .orderStatus(e.getOrderStatus())
                        .quantity(e.getQuantity())
                        .startTime(hhmm(e.getStartTime()))
                        .workers(new ArrayList<>())
                        .czsIds(new ArrayList<>())
                        .build();
                byGroup.put(key, g);
            }
            String worker = e.getWorkerName();
            if (worker != null && !worker.isBlank() && !g.getWorkers().contains(worker)) {
                g.getWorkers().add(worker);
            }
            g.getCzsIds().add(e.getCzsId());
            String hm = hhmm(e.getStartTime());
            if (hm.compareTo(g.getStartTime()) < 0) {
                g.setStartTime(hm);
            }
        }
        return new ArrayList<>(byGroup.values());
    }

    /** Escape SQL LIKE wildcards via bracket-escaping (no ESCAPE clause needed). */
    private static String escapeLike(String s) {
        return s.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]");
    }

    /** Availability windows per worker (by trimmed name) for a single day. */
    private Map<String, List<AvailabilityIntervalDto>> loadAvailability(LocalDate day) {
        Map<String, List<AvailabilityIntervalDto>> byWorker = new LinkedHashMap<>();
        for (Object[] r : repository.findAvailability(day)) {
            String worker = r[0] == null ? "" : r[0].toString().trim();
            boolean wholeDay = toInt(r[3]) != null && toInt(r[3]) != 0;
            AvailabilityIntervalDto interval = wholeDay
                    ? AvailabilityIntervalDto.builder().from("00:00").to("24:00").build()
                    : AvailabilityIntervalDto.builder()
                        .from(hhmm(toLocalDateTime(r[1])))
                        .to(hhmm(toLocalDateTime(r[2])))
                        .build();
            byWorker.computeIfAbsent(worker, k -> new ArrayList<>()).add(interval);
        }
        return byWorker;
    }

    private static String hhmm(LocalDateTime t) {
        if (t == null) {
            return "00:00";
        }
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    /** One availability window of a resource, as concrete datetimes. */
    public record AvailInterval(LocalDateTime from, LocalDateTime to) {}

    /**
     * Effective work minutes of the window [start, end]:
     * <ul>
     *   <li><b>single-day</b> (same start/end date): the FULL window (end - start),
     *       regardless of availability;</li>
     *   <li><b>multi-day</b>: only the parts of [start, end] that fall inside the
     *       given availability windows — non-available gaps are excluded.</li>
     * </ul>
     */
    static long effectiveMinutes(LocalDateTime start, LocalDateTime end, List<AvailInterval> availability) {
        if (!end.isAfter(start)) {
            return 0;
        }
        if (start.toLocalDate().equals(end.toLocalDate())) {
            return Duration.between(start, end).toMinutes();
        }
        long sum = 0;
        for (AvailInterval iv : availability) {
            LocalDateTime s = start.isAfter(iv.from()) ? start : iv.from();
            LocalDateTime e = end.isBefore(iv.to()) ? end : iv.to();
            if (s.isBefore(e)) {
                sum += Duration.between(s, e).toMinutes();
            }
        }
        return sum;
    }

    /** Per-unit CzasPracy in hours = effectiveMinutes / 60 / ceil(qty / divisor) (mult=1 if divisor<=0). */
    static BigDecimal czasPracyHours(long effMinutes, BigDecimal qty, BigDecimal divisor) {
        long mult = 1;
        if (divisor != null && divisor.signum() > 0 && qty != null && qty.signum() > 0) {
            mult = (long) Math.ceil(qty.doubleValue() / divisor.doubleValue());
            if (mult < 1) {
                mult = 1;
            }
        }
        return BigDecimal.valueOf(effMinutes).divide(BigDecimal.valueOf(60L * mult), 4, RoundingMode.HALF_UP);
    }

    /**
     * Move/resize a plan row on the timeline. Snaps both ends to 15 min, then writes
     * the window ({@code CZS_CzasStart}/{@code CZS_CzasEnd}) AND recomputes
     * {@code CZS_CzasPracy} (see {@link #effectiveMinutes}/{@link #czasPracyHours}) so
     * Comarch — which derives the resource end from CzasPracy — keeps the edit.
     */
    public GrafikEntryDto updateAssignment(Integer czsId, LocalDateTime rawStart, LocalDateTime rawEnd) {
        if (rawStart == null || rawEnd == null) {
            throw new IllegalArgumentException("startTime and endTime are required");
        }
        LocalDateTime start = snapTo15(rawStart);
        LocalDateTime end = snapTo15(rawEnd);
        long minutes = Duration.between(start, end).toMinutes();
        // end may equal start (zero-length is allowed by the ERP); only reject end < start
        if (minutes < 0) {
            throw new IllegalArgumentException("End must not be before start (start=" + start + ", end=" + end + ")");
        }

        CtiZasobDok row = repository.findById(czsId)
                .orElseThrow(() -> new IllegalArgumentException("No grafik row with czsId=" + czsId));

        long effMin;
        if (start.toLocalDate().equals(end.toLocalDate())) {
            effMin = minutes;
        } else {
            List<AvailInterval> avail = loadResourceAvailability(row.getResourceId(), start.toLocalDate(), end.toLocalDate());
            effMin = effectiveMinutes(start, end, avail);
        }
        BigDecimal czasPracy = czasPracyHours(effMin, repository.findOrderQuantity(czsId), row.getDivisor());
        log.info("[updateAssignment] czsId={} start={} end={} effMin={} czasPracy={}", czsId, start, end, effMin, czasPracy);

        int updated = repository.updateWindow(czsId, start, end, czasPracy);
        if (updated == 0) {
            throw new IllegalArgumentException("No grafik row with czsId=" + czsId);
        }

        return GrafikEntryDto.builder()
                .czsId(czsId)
                .plannedHours(czasPracy)
                .plannedMinutes(toMinutes(czasPracy))
                .timeUnit(2)
                .startTime(start)
                .endTime(end)
                .quantity(row.getQuantity())
                .finished(row.getFinished())
                .build();
    }

    private List<AvailInterval> loadResourceAvailability(Integer czid, LocalDate from, LocalDate to) {
        List<AvailInterval> out = new ArrayList<>();
        if (czid == null) {
            return out;
        }
        for (Object[] r : repository.findResourceAvailability(czid, from, to)) {
            LocalDateTime f = toLocalDateTime(r[0]);
            LocalDateTime t = toLocalDateTime(r[1]);
            if (f != null && t != null) {
                out.add(new AvailInterval(f, t));
            }
        }
        return out;
    }

    /** Snap a timestamp to the nearest 15 minutes (seconds/nanos dropped). */
    LocalDateTime snapTo15(LocalDateTime t) {
        int minutesOfDay = t.getHour() * 60 + t.getMinute()
                + (t.getSecond() >= 30 ? 1 : 0);
        int snapped = (int) (Math.round(minutesOfDay / (double) SNAP_MINUTES) * SNAP_MINUTES);
        int capped = Math.min(snapped, 24 * 60);
        LocalDateTime base = t.toLocalDate().atStartOfDay();
        if (capped >= 24 * 60) {
            return base.plusDays(1); // midnight next day
        }
        return LocalDateTime.of(t.toLocalDate(), LocalTime.of(capped / 60, capped % 60));
    }

    private GrafikEntryDto mapRow(Object[] r) {
        BigDecimal plannedHours = toBigDecimal(r[8]);
        return GrafikEntryDto.builder()
                .czsId(toInt(r[0]))
                .workerName(r[1] == null ? null : r[1].toString().trim())
                .resourceId(toInt(r[2]))
                .orderId(toInt(r[3]))
                .orderNumber(r[4] == null ? null : r[4].toString().trim())
                .contractorName(r[13] == null ? null : r[13].toString().trim())
                .productCode(r[5] == null ? null : r[5].toString().trim())
                .quantity(toBigDecimal(r[6]))
                .orderStatus(toInt(r[7]))
                .plannedHours(plannedHours)
                .plannedMinutes(toMinutes(plannedHours))
                .timeUnit(toInt(r[9]))
                .startTime(toLocalDateTime(r[10]))
                .endTime(toLocalDateTime(r[11]))
                .finished(toInt(r[12]))
                .build();
    }

    private Integer toMinutes(BigDecimal hours) {
        if (hours == null) {
            return null;
        }
        return hours.multiply(SIXTY).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static Integer toInt(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(o.toString());
    }

    private static LocalDateTime toLocalDateTime(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (o instanceof LocalDateTime ldt) {
            return ldt;
        }
        return null;
    }
}
