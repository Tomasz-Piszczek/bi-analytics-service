package org.example.bianalyticsservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.controller.analytics.dto.*;
import org.example.bianalyticsservice.repository.MaterialAuditRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialAuditService {

    private final MaterialAuditRepository materialAuditRepository;
    private final WorkerAnalyticsCacheService workerAnalyticsCacheService;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public MaterialAuditResponseDto getMaterialAudit(MaterialAuditRequestDto request) {
        log.info("[getMaterialAudit] dateFrom={} dateTo={} offsetPercent={} offsetNumber={}",
                request.getDateFrom(), request.getDateTo(),
                request.getOffsetPercent(), request.getOffsetNumber());

        List<Object[]> expectedRows = materialAuditRepository.findExpectedMaterials(
                request.getDateFrom(), request.getDateTo());
        List<Object[]> actualRows = materialAuditRepository.findActualMaterials(
                request.getDateFrom(), request.getDateTo());

        // KROK 1: Zbuduj mapę zleceń z oczekiwanymi materiałami
        Map<Integer, OrderRawData> orderMap = new LinkedHashMap<>();

        for (Object[] row : expectedRows) {
            Integer orderId          = (Integer)   row[0];
            String numerZlecenia     = (String)    row[1];
            LocalDate dataZlecenia   = row[2] != null ? ((Date) row[2]).toLocalDate() : null;
            String productTypeId     = (String)    row[3];
            String expectedTwrKod    = (String)    row[4];
            BigDecimal expectedIlosc = row[5] != null ? new BigDecimal(row[5].toString()) : null;

            OrderRawData order = orderMap.computeIfAbsent(orderId, k ->
                    new OrderRawData(orderId, numerZlecenia, dataZlecenia, productTypeId));

            if (expectedTwrKod != null && expectedIlosc != null) {
                order.expected.put(expectedTwrKod, expectedIlosc);
            }
        }

        // KROK 2: Dołóż faktycznie wpisane materiały per pracownik
        for (Object[] row : actualRows) {
            Integer orderId        = (Integer)   row[0];
            String pracownik       = row[1] != null ? ((String) row[1]).trim() : null;
            String actualTwrKod    = (String)    row[2];
            BigDecimal actualIlosc = row[3] != null ? new BigDecimal(row[3].toString()) : null;

            OrderRawData order = orderMap.get(orderId);
            if (order == null) continue;

            if (pracownik != null && actualTwrKod != null && actualIlosc != null) {
                order.actualByWorker
                        .computeIfAbsent(pracownik, k -> new HashMap<>())
                        .merge(actualTwrKod, actualIlosc, BigDecimal::add);
            }
        }

        // KROK 3: Dla każdego zlecenia porównaj oczekiwane vs wpisane
        Map<String, List<AuditOrderDto>> correctByWorker   = new LinkedHashMap<>();
        Map<String, List<AuditOrderDto>> incorrectByWorker = new LinkedHashMap<>();

        for (OrderRawData order : orderMap.values()) {
            Map<String, BigDecimal> totalActual = new HashMap<>();
            for (Map<String, BigDecimal> workerEntries : order.actualByWorker.values()) {
                workerEntries.forEach((kod, ilosc) ->
                        totalActual.merge(kod, ilosc, BigDecimal::add));
            }

            List<AuditMaterialEntryDto> materials = buildMaterialAudit(
                    order.expected, totalActual,
                    request.getOffsetPercent(), request.getOffsetNumber()
            );

            boolean orderOk = materials.stream().allMatch(AuditMaterialEntryDto::isOk);

            Set<String> workers = order.actualByWorker.isEmpty()
                    ? Collections.singleton("BRAK RW")
                    : order.actualByWorker.keySet();

            AuditOrderDto auditOrder = AuditOrderDto.builder()
                    .numerZlecenia(order.numerZlecenia)
                    .dataZlecenia(order.dataZlecenia)
                    .productTypeId(order.productTypeId)
                    .materials(materials)
                    .orderOk(orderOk)
                    .workerTimeEntries(new ArrayList<>())
                    .build();

            for (String worker : workers) {
                if (orderOk) {
                    correctByWorker.computeIfAbsent(worker, k -> new ArrayList<>()).add(auditOrder);
                } else {
                    incorrectByWorker.computeIfAbsent(worker, k -> new ArrayList<>()).add(auditOrder);
                }
            }
        }

        // KROK 4: Złóż response
        Set<String> allWorkers = new LinkedHashSet<>();
        allWorkers.addAll(incorrectByWorker.keySet());
        allWorkers.addAll(correctByWorker.keySet());

        List<WorkerAuditDto> workerAudits = allWorkers.stream()
                .map(worker -> {
                    List<AuditOrderDto> correct   = correctByWorker.getOrDefault(worker, Collections.emptyList());
                    List<AuditOrderDto> incorrect = incorrectByWorker.getOrDefault(worker, Collections.emptyList());
                    return WorkerAuditDto.builder()
                            .workerId(worker)
                            .correctOrders(correct)
                            .incorrectOrders(incorrect)
                            .totalOrders(correct.size() + incorrect.size())
                            .correctCount(correct.size())
                            .incorrectCount(incorrect.size())
                            .build();
                })
                .sorted(Comparator.comparingInt(w -> -w.getIncorrectCount()))
                .collect(Collectors.toList());

        int totalOrders  = orderMap.size();
        int totalCorrect = (int) orderMap.values().stream()
                .filter(o -> isOrderCorrect(o, request))
                .count();

        // Enrich orders with worker time data
        enrichOrdersWithWorkerTime(workerAudits, request.getDateFrom(), request.getDateTo());

        return MaterialAuditResponseDto.builder()
                .workers(workerAudits)
                .totalOrders(totalOrders)
                .totalCorrect(totalCorrect)
                .totalIncorrect(totalOrders - totalCorrect)
                .build();
    }

    private List<AuditMaterialEntryDto> buildMaterialAudit(
            Map<String, BigDecimal> expected,
            Map<String, BigDecimal> actual,
            BigDecimal offsetPercent,
            BigDecimal offsetNumber
    ) {
        List<AuditMaterialEntryDto> result = new ArrayList<>();
        Set<String> processedKods = new HashSet<>();

        for (Map.Entry<String, BigDecimal> entry : expected.entrySet()) {
            String kod = entry.getKey();
            BigDecimal expectedIlosc = entry.getValue();
            BigDecimal actualIlosc   = actual.get(kod);
            processedKods.add(kod);

            result.add(AuditMaterialEntryDto.builder()
                    .twrKod(kod)
                    .expectedIlosc(expectedIlosc)
                    .actualIlosc(actualIlosc)
                    .ok(isWithinOffset(expectedIlosc, actualIlosc, offsetPercent, offsetNumber))
                    .build());
        }

        for (Map.Entry<String, BigDecimal> entry : actual.entrySet()) {
            String kod = entry.getKey();
            if (processedKods.contains(kod)) continue;

            result.add(AuditMaterialEntryDto.builder()
                    .twrKod(kod)
                    .expectedIlosc(null)
                    .actualIlosc(entry.getValue())
                    .ok(false)
                    .build());
        }

        return result;
    }

    private boolean isWithinOffset(BigDecimal expected, BigDecimal actual,
                                   BigDecimal offsetPercent, BigDecimal offsetNumber) {
        if (actual == null) return false;
        if (expected == null) return false;

        BigDecimal diffNumber = expected.subtract(actual).abs();

        boolean numberOk = offsetNumber != null && diffNumber.compareTo(offsetNumber) <= 0;

        boolean percentOk = false;
        if (offsetPercent != null && expected.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal percentRange = expected.multiply(offsetPercent)
                    .divide(HUNDRED, 4, RoundingMode.HALF_UP);
            percentOk = diffNumber.compareTo(percentRange) <= 0;
        }

        return numberOk || percentOk;
    }

    private boolean isOrderCorrect(OrderRawData order, MaterialAuditRequestDto request) {
        Map<String, BigDecimal> totalActual = new HashMap<>();
        for (Map<String, BigDecimal> workerEntries : order.actualByWorker.values()) {
            workerEntries.forEach((kod, ilosc) -> totalActual.merge(kod, ilosc, BigDecimal::add));
        }
        List<AuditMaterialEntryDto> materials = buildMaterialAudit(
                order.expected, totalActual,
                request.getOffsetPercent(), request.getOffsetNumber()
        );
        return materials.stream().allMatch(AuditMaterialEntryDto::isOk);
    }

    private void enrichOrdersWithWorkerTime(List<WorkerAuditDto> workerAudits,
                                             LocalDate dateFrom, LocalDate dateTo) {
        // Fetch all jobs with worker time data
        List<JobDto> allJobs = workerAnalyticsCacheService.getAllJobs();

        // Build map: numerZlecenia -> Map<workerId, totalMinutes>
        Map<String, Map<String, BigDecimal>> jobWorkerTimeMap = new HashMap<>();

        for (JobDto job : allJobs) {
            for (WorkerTimeDto workerTime : job.getWorkers()) {
                // Filter by date range
                if (workerTime.getWorkDate() != null &&
                    (workerTime.getWorkDate().isBefore(dateFrom) ||
                     workerTime.getWorkDate().isAfter(dateTo))) {
                    continue;
                }

                // Aggregate minutes by workerId for this job
                jobWorkerTimeMap
                    .computeIfAbsent(job.getNumerZlecenia(), k -> new HashMap<>())
                    .merge(workerTime.getWorkerId(), workerTime.getMinutesWorked(), BigDecimal::add);
            }
        }

        // Populate workerTimeEntries for each order
        for (WorkerAuditDto workerAudit : workerAudits) {
            for (AuditOrderDto order : workerAudit.getCorrectOrders()) {
                populateWorkerTimeEntries(order, jobWorkerTimeMap);
            }
            for (AuditOrderDto order : workerAudit.getIncorrectOrders()) {
                populateWorkerTimeEntries(order, jobWorkerTimeMap);
            }
        }
    }

    private void populateWorkerTimeEntries(AuditOrderDto order,
                                           Map<String, Map<String, BigDecimal>> jobWorkerTimeMap) {
        Map<String, BigDecimal> workerTimes = jobWorkerTimeMap.get(order.getNumerZlecenia());

        if (workerTimes != null && !workerTimes.isEmpty()) {
            List<WorkerTimeEntryDto> timeEntries = workerTimes.entrySet().stream()
                .map(entry -> WorkerTimeEntryDto.builder()
                    .workerId(entry.getKey())
                    .minutesWorked(entry.getValue())
                    .build())
                .collect(Collectors.toList());

            order.setWorkerTimeEntries(timeEntries);
        }
    }

    private static class OrderRawData {
        final Integer orderId;
        final String numerZlecenia;
        final LocalDate dataZlecenia;
        final String productTypeId;
        final Map<String, BigDecimal> expected = new LinkedHashMap<>();
        final Map<String, Map<String, BigDecimal>> actualByWorker = new LinkedHashMap<>();

        OrderRawData(Integer orderId, String numerZlecenia,
                     LocalDate dataZlecenia, String productTypeId) {
            this.orderId = orderId;
            this.numerZlecenia = numerZlecenia;
            this.dataZlecenia = dataZlecenia;
            this.productTypeId = productTypeId;
        }
    }
}