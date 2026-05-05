package org.example.bianalyticsservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.controller.analytics.dto.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfitabilityService {

    private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");

    private final WorkerAnalyticsCacheService cacheService;
    private final WorkerStatsCalculator workerStatsCalculator;
    private final RevenueAllocator revenueAllocator;

    public JobProfitabilityResponseDto getJobProfitability(JobProfitabilityRequestDto req) {
        BigDecimal hourlyRate = req.getHourlyRate() == null ? BigDecimal.ZERO : req.getHourlyRate();
        boolean ignoreInternal = Boolean.TRUE.equals(req.getIgnoreInternalWork());

        List<JobDto> allJobs = cacheService.getAllJobs();
        Map<Integer, List<InvoiceLinkDto>> linksByCzn = cacheService.getAllInvoiceLinks();

        // Build per-RO sibling-quantity map (productCode -> sum of CZN_Ilosc) for accurate allocation
        Map<Integer, Map<String, BigDecimal>> siblingQtyByRoCode = buildSiblingQtyMap(allJobs, linksByCzn);

        int hiddenUninvoiced = 0;
        List<JobProfitabilityRowDto> rows = new ArrayList<>();

        for (JobDto job : allJobs) {
            // Filters
            if (ignoreInternal && workerStatsCalculator.isInternalWorkJob(job)) continue;
            if (req.getSelectedProducts() != null && !req.getSelectedProducts().isEmpty()
                    && !req.getSelectedProducts().contains(job.getProductTypeId())) continue;
            if (req.getExcludedWorkers() != null && !req.getExcludedWorkers().isEmpty()
                    && job.getWorkers() != null
                    && job.getWorkers().stream().anyMatch(w -> req.getExcludedWorkers().contains(w.getWorkerId()))) {
                continue;
            }

            // Find an FS link in the requested invoice-date window. We require an EXACT match
            // between the ZP's full product code and an FS line; otherwise the ZP is "no invoice".
            // Don't fall back to a non-matching link as a "chosen" FS — it misleads users.
            List<InvoiceLinkDto> links = linksByCzn.getOrDefault(job.getId(), Collections.emptyList());
            InvoiceLinkDto matched = pickMatchingInvoiceLinkInWindow(links, req.getDateFrom(), req.getDateTo(), job);
            // Any-link is used solely to attach the RO reference to a NO_INVOICE row (so the user
            // can drill in to see what got linked). It is NEVER reported as the chosen FS.
            InvoiceLinkDto anyLink = matched != null ? matched : pickAnyLinkInWindow(links, req.getDateFrom(), req.getDateTo());

            Integer roIdForSiblings = matched != null ? matched.getRoId() : (anyLink != null ? anyLink.getRoId() : null);
            Map<String, BigDecimal> siblingQty = roIdForSiblings != null
                    ? siblingQtyByRoCode.getOrDefault(roIdForSiblings, Collections.emptyMap())
                    : Collections.emptyMap();
            String allocationCode = (matched != null && matched.getJobProductCodeFull() != null)
                    ? matched.getJobProductCodeFull()
                    : (anyLink != null && anyLink.getJobProductCodeFull() != null
                            ? anyLink.getJobProductCodeFull()
                            : job.getProductTypeId());
            // Allocator with matched=null returns 0 zł / NO_INVOICE; with a real match it returns HIGH.
            // We override to HIDDEN below when the ZP has no link at all (anyLink == null).
            RevenueAllocator.Allocation alloc = revenueAllocator.allocate(
                    allocationCode, job.getQuantity(), matched, siblingQty);
            RevenueAllocator.Confidence effectiveConfidence = (matched == null && anyLink == null)
                    ? RevenueAllocator.Confidence.HIDDEN
                    : alloc.getConfidence();

            if (!confidencePassesFilter(effectiveConfidence, req)) continue;
            if (effectiveConfidence == RevenueAllocator.Confidence.HIDDEN) hiddenUninvoiced++;

            BigDecimal materialCost = job.getRwSuma() == null ? BigDecimal.ZERO : job.getRwSuma();
            BigDecimal totalMin = job.getTotalMinutes() == null ? BigDecimal.ZERO : job.getTotalMinutes();
            BigDecimal laborCost = totalMin.divide(MINUTES_PER_HOUR, 4, RoundingMode.HALF_UP).multiply(hourlyRate);
            BigDecimal profit = alloc.getRevenue().subtract(materialCost).subtract(laborCost);
            BigDecimal margin = alloc.getRevenue().signum() > 0
                    ? profit.multiply(new BigDecimal("100")).divide(alloc.getRevenue(), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // Worker breakdown — group (workerId, resourceId), aggregate hours, allocate share
            List<JobProfitabilityWorkerDto> workers = buildWorkerBreakdown(job, profit, hourlyRate, totalMin);

            // FS fields come ONLY from the matching link. RO is shown either way (the matching
            // RO if matched, or the any-link RO so the user can drill in).
            String fsNumer = matched != null ? matched.getFsNumer() : null;
            LocalDate fsDate = matched != null ? matched.getFsDate() : null;
            String roNumer = matched != null ? matched.getRoNumer() : (anyLink != null ? anyLink.getRoNumer() : null);
            Integer coBundled = matched != null ? matched.getCoBundledZpCount() : null;
            boolean fskorApplied = matched != null && matched.getFsKorrectionNet() != null
                    && matched.getFsKorrectionNet().signum() != 0;

            rows.add(JobProfitabilityRowDto.builder()
                    .cznId(job.getId())
                    .numerZlecenia(job.getNumerZlecenia())
                    .productTypeId(job.getProductTypeId())
                    .orderQuantity(job.getQuantity())
                    .orderDate(job.getDate())
                    .invoiceNumber(fsNumer)
                    .invoiceDate(fsDate)
                    .roNumer(roNumer)
                    .coBundledZpCount(coBundled)
                    .revenueAttributionConfidence(effectiveConfidence.name())
                    .revenue(alloc.getRevenue())
                    .materialCost(materialCost.setScale(2, RoundingMode.HALF_UP))
                    .laborMinutes(totalMin)
                    .laborHours(totalMin.divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP))
                    .laborCost(laborCost.setScale(2, RoundingMode.HALF_UP))
                    .profit(profit.setScale(2, RoundingMode.HALF_UP))
                    .marginPct(margin)
                    .workers(workers)
                    .rwElements(job.getRwElements())
                    .hasMaterialCost(materialCost.signum() > 0)
                    .hasLaborTime(totalMin.signum() > 0)
                    .fskorAdjustmentApplied(fskorApplied)
                    .build());
        }

        sortRows(rows, req.getSortBy());

        // Summary
        BigDecimal totRev = sum(rows, JobProfitabilityRowDto::getRevenue);
        BigDecimal totMat = sum(rows, JobProfitabilityRowDto::getMaterialCost);
        BigDecimal totLab = sum(rows, JobProfitabilityRowDto::getLaborCost);
        BigDecimal totProf = sum(rows, JobProfitabilityRowDto::getProfit);
        BigDecimal avgMargin = totRev.signum() > 0
                ? totProf.multiply(new BigDecimal("100")).divide(totRev, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        int withInvoice = (int) rows.stream().filter(r -> "HIGH".equals(r.getRevenueAttributionConfidence())).count();
        int noInvoice   = (int) rows.stream().filter(r -> "NO_INVOICE".equals(r.getRevenueAttributionConfidence())).count();

        // Top/bottom 5 by absolute profit (independent of sortBy chosen for the main list)
        List<JobProfitabilityRowDto> sortedByProfit = rows.stream()
                .sorted(Comparator.comparing(JobProfitabilityRowDto::getProfit).reversed())
                .toList();
        List<JobProfitabilityRowDto> top5 = sortedByProfit.stream().limit(5).toList();
        List<JobProfitabilityRowDto> bottom5 = sortedByProfit.size() <= 5
                ? Collections.emptyList()
                : sortedByProfit.subList(Math.max(0, sortedByProfit.size() - 5), sortedByProfit.size())
                  .stream().sorted(Comparator.comparing(JobProfitabilityRowDto::getProfit)).toList();

        JobProfitabilitySummaryDto summary = JobProfitabilitySummaryDto.builder()
                .totalJobs(rows.size())
                .withInvoiceJobs(withInvoice)
                .noInvoiceJobs(noInvoice)
                .hiddenUninvoicedJobs(hiddenUninvoiced)
                .totalRevenue(totRev.setScale(2, RoundingMode.HALF_UP))
                .totalMaterialCost(totMat.setScale(2, RoundingMode.HALF_UP))
                .totalLaborCost(totLab.setScale(2, RoundingMode.HALF_UP))
                .totalProfit(totProf.setScale(2, RoundingMode.HALF_UP))
                .avgMarginPct(avgMargin)
                .top5(top5)
                .bottom5(bottom5)
                .build();

        return JobProfitabilityResponseDto.builder().rows(rows).summary(summary).build();
    }

    public WorkerCashResponseDto getWorkerCashContribution(WorkerCashRequestDto req) {
        // Reuse the job-profit computation, then fan out per (worker, resource).
        JobProfitabilityRequestDto inner = JobProfitabilityRequestDto.builder()
                .dateFrom(req.getDateFrom())
                .dateTo(req.getDateTo())
                .hourlyRate(req.getHourlyRate())
                .selectedProducts(req.getSelectedProducts())
                .excludedWorkers(req.getExcludedWorkers())
                .ignoreInternalWork(req.getIgnoreInternalWork())
                .minConfidence(req.getMinConfidence())
                .confidenceFilter(req.getConfidenceFilter())
                .sortBy("PROFIT_DESC")
                .build();
        JobProfitabilityResponseDto jobView = getJobProfitability(inner);

        // Aggregate per (workerId, resourceId)
        Map<String, WorkerAggregator> agg = new LinkedHashMap<>();
        Map<String, List<WorkerCashTopJobDto>> jobsPerWorker = new LinkedHashMap<>();

        for (JobProfitabilityRowDto row : jobView.getRows()) {
            BigDecimal totalRowMinutes = row.getLaborMinutes();
            if (totalRowMinutes == null || totalRowMinutes.signum() == 0) continue;

            for (JobProfitabilityWorkerDto w : row.getWorkers()) {
                String key = w.getWorkerId() + "|" + (w.getResourceId() == null ? w.getWorkerId() : w.getResourceId());
                BigDecimal share = w.getMinutes().divide(totalRowMinutes, 6, RoundingMode.HALF_UP);

                WorkerAggregator a = agg.computeIfAbsent(key, k -> new WorkerAggregator(w.getWorkerId(), w.getResourceId()));
                a.totalMinutes = a.totalMinutes.add(w.getMinutes());
                a.attributedRevenue = a.attributedRevenue.add(row.getRevenue().multiply(share));
                a.attributedMaterialCost = a.attributedMaterialCost.add(row.getMaterialCost().multiply(share));
                a.attributedLaborCost = a.attributedLaborCost.add(w.getLaborCost());
                a.jobCount += 1;

                jobsPerWorker.computeIfAbsent(key, k -> new ArrayList<>()).add(WorkerCashTopJobDto.builder()
                        .numerZlecenia(row.getNumerZlecenia())
                        .productTypeId(row.getProductTypeId())
                        .invoiceNumber(row.getInvoiceNumber())
                        .myMinutes(w.getMinutes())
                        .myShareOfProfit(w.getShareOfProfit())
                        .build());
            }
        }

        List<WorkerCashRowDto> rows = agg.values().stream().map(a -> {
            BigDecimal profit = a.attributedRevenue.subtract(a.attributedMaterialCost).subtract(a.attributedLaborCost);
            BigDecimal hours = a.totalMinutes.divide(MINUTES_PER_HOUR, 4, RoundingMode.HALF_UP);
            BigDecimal pph = hours.signum() > 0
                    ? profit.divide(hours, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            String key = a.workerId + "|" + (a.resourceId == null ? a.workerId : a.resourceId);
            List<WorkerCashTopJobDto> myJobs = jobsPerWorker.getOrDefault(key, Collections.emptyList());
            List<WorkerCashTopJobDto> top = myJobs.stream()
                    .sorted(Comparator.comparing(WorkerCashTopJobDto::getMyShareOfProfit, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(5).collect(Collectors.toList());
            List<WorkerCashTopJobDto> worst = myJobs.stream()
                    .sorted(Comparator.comparing(WorkerCashTopJobDto::getMyShareOfProfit, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .limit(3).collect(Collectors.toList());

            return WorkerCashRowDto.builder()
                    .workerId(a.workerId)
                    .resourceId(a.resourceId)
                    .totalMinutes(a.totalMinutes)
                    .totalHours(hours.setScale(2, RoundingMode.HALF_UP))
                    .jobsTouched(a.jobCount)
                    .attributedRevenue(a.attributedRevenue.setScale(2, RoundingMode.HALF_UP))
                    .attributedMaterialCost(a.attributedMaterialCost.setScale(2, RoundingMode.HALF_UP))
                    .attributedLaborCost(a.attributedLaborCost.setScale(2, RoundingMode.HALF_UP))
                    .attributedProfit(profit.setScale(2, RoundingMode.HALF_UP))
                    .profitPerHour(pph)
                    .topJobs(top)
                    .worstJobs(worst)
                    .build();
        }).sorted(Comparator.comparing(WorkerCashRowDto::getProfitPerHour).reversed())
        .collect(Collectors.toList());

        BigDecimal totalProfit = rows.stream().map(WorkerCashRowDto::getAttributedProfit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRevenue = rows.stream().map(WorkerCashRowDto::getAttributedRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);

        return WorkerCashResponseDto.builder()
                .rows(rows)
                .totalAttributedProfit(totalProfit.setScale(2, RoundingMode.HALF_UP))
                .totalAttributedRevenue(totalRevenue.setScale(2, RoundingMode.HALF_UP))
                .totalJobsAnalyzed(jobView.getRows().size())
                .build();
    }

    // -------------------- helpers --------------------

    private List<JobProfitabilityWorkerDto> buildWorkerBreakdown(
            JobDto job, BigDecimal jobProfit, BigDecimal hourlyRate, BigDecimal totalMinutes) {
        if (job.getWorkers() == null || job.getWorkers().isEmpty()) return Collections.emptyList();

        // Group by (workerId, resourceId), summing minutes (exactly like aggregateWorkersForDisplay)
        Map<String, JobProfitabilityWorkerDto> map = new LinkedHashMap<>();
        for (WorkerTimeDto w : job.getWorkers()) {
            String resourceId = w.getResourceId() != null ? w.getResourceId() : w.getWorkerId();
            String key = w.getWorkerId() + "|" + resourceId;
            JobProfitabilityWorkerDto existing = map.get(key);
            BigDecimal min = w.getMinutesWorked() == null ? BigDecimal.ZERO : w.getMinutesWorked();
            if (existing == null) {
                map.put(key, JobProfitabilityWorkerDto.builder()
                        .workerId(w.getWorkerId())
                        .resourceId(resourceId)
                        .minutes(min)
                        .build());
            } else {
                existing.setMinutes(existing.getMinutes().add(min));
            }
        }

        // Compute laborCost, share, etc.
        BigDecimal totMin = totalMinutes == null || totalMinutes.signum() == 0 ? BigDecimal.ONE : totalMinutes;
        return map.values().stream().map(w -> {
            BigDecimal hours = w.getMinutes().divide(MINUTES_PER_HOUR, 4, RoundingMode.HALF_UP);
            BigDecimal labor = hours.multiply(hourlyRate);
            BigDecimal sharePct = w.getMinutes().multiply(new BigDecimal("100")).divide(totMin, 2, RoundingMode.HALF_UP);
            BigDecimal shareOfProfit = jobProfit.multiply(w.getMinutes()).divide(totMin, 2, RoundingMode.HALF_UP);
            w.setHours(hours.setScale(2, RoundingMode.HALF_UP));
            w.setLaborCost(labor.setScale(2, RoundingMode.HALF_UP));
            w.setSharePct(sharePct);
            w.setShareOfProfit(shareOfProfit);
            return w;
        }).collect(Collectors.toList());
    }

    /**
     * Returns a link whose FS lines contain an exact match for the ZP's full product code,
     * preferring the most recent match. Returns null when no link matches — even if the ZP
     * has linked FSs that simply don't bill its product. Don't fall back to "the most recent
     * any-link"; emitting a non-matching FS as the chosen invoice misleads users into thinking
     * the row was billed by an FS that doesn't actually contain their product.
     */
    private InvoiceLinkDto pickMatchingInvoiceLinkInWindow(List<InvoiceLinkDto> links, LocalDate from, LocalDate to, JobDto job) {
        if (links == null || links.isEmpty()) return null;
        InvoiceLinkDto bestMatch = null;
        for (InvoiceLinkDto l : links) {
            if (l.getFsDate() == null) continue;
            if (from != null && l.getFsDate().isBefore(from)) continue;
            if (to != null && l.getFsDate().isAfter(to)) continue;

            String code = l.getJobProductCodeFull() != null ? l.getJobProductCodeFull() : job.getProductTypeId();
            boolean matches = code != null && l.getFsLines() != null
                    && l.getFsLines().stream().anyMatch(line -> code.equals(line.getTwrKod()));
            if (matches && (bestMatch == null || l.getFsDate().isAfter(bestMatch.getFsDate()))) {
                bestMatch = l;
            }
        }
        return bestMatch;
    }

    /** Any link in the window — used only to surface the RO on a NO_INVOICE row, never as FS. */
    private InvoiceLinkDto pickAnyLinkInWindow(List<InvoiceLinkDto> links, LocalDate from, LocalDate to) {
        if (links == null || links.isEmpty()) return null;
        InvoiceLinkDto best = null;
        for (InvoiceLinkDto l : links) {
            if (l.getFsDate() == null) continue;
            if (from != null && l.getFsDate().isBefore(from)) continue;
            if (to != null && l.getFsDate().isAfter(to)) continue;
            if (best == null || l.getFsDate().isAfter(best.getFsDate())) best = l;
        }
        return best;
    }

    private Map<Integer, Map<String, BigDecimal>> buildSiblingQtyMap(
            List<JobDto> allJobs, Map<Integer, List<InvoiceLinkDto>> linksByCzn) {

        // For each ZP, look up its RO(s) via the link map; index ZP FULL product code & qty per RO.
        // Keying by the full code (from CDN.Towary, carried on the link) keeps allocation correct
        // when two distinct full codes share the same truncated CZN_TwrKod prefix.
        // IMPORTANT: a ZP can appear in multiple link rows for the SAME RO (one row per linked FS,
        // and the RO->WZ->FS path adds even more). We must add this ZP's qty ONCE per unique RO,
        // not once per link, otherwise sibling_qty doubles and revenue gets halved.
        Map<Integer, Map<String, BigDecimal>> result = new HashMap<>();
        for (JobDto job : allJobs) {
            List<InvoiceLinkDto> links = linksByCzn.get(job.getId());
            if (links == null || links.isEmpty()) continue;
            BigDecimal qty = job.getQuantity() == null ? BigDecimal.ONE : job.getQuantity();
            // Per ZP: collapse links to (roId, fullCode) pairs first, then merge once per pair.
            Set<Map.Entry<Integer, String>> seen = new HashSet<>();
            for (InvoiceLinkDto link : links) {
                String code = link.getJobProductCodeFull() != null
                        ? link.getJobProductCodeFull()
                        : job.getProductTypeId();
                if (code == null || link.getRoId() == null) continue;
                if (!seen.add(Map.entry(link.getRoId(), code))) continue;
                result.computeIfAbsent(link.getRoId(), k -> new HashMap<>())
                      .merge(code, qty, BigDecimal::add);
            }
        }
        return result;
    }

    /**
     * {@code confidenceFilter}: ALL / WITH_INVOICE / NO_INVOICE / HIDDEN. ALL excludes HIDDEN
     * (preserving the original "rows that exist" semantics) so HIDDEN ZPs only show when
     * explicitly requested. HIGH/NO_INVOICE filters narrow to that one bucket.
     * Legacy {@code minConfidence=HIGH} is still honored for backward compatibility.
     */
    private boolean confidencePassesFilter(RevenueAllocator.Confidence c, JobProfitabilityRequestDto req) {
        String f = req.getConfidenceFilter();
        if (f != null && !f.isBlank()) {
            return switch (f.toUpperCase()) {
                case "WITH_INVOICE" -> c == RevenueAllocator.Confidence.HIGH;
                case "NO_INVOICE"   -> c == RevenueAllocator.Confidence.NO_INVOICE;
                case "HIDDEN"       -> c == RevenueAllocator.Confidence.HIDDEN;
                default              -> c != RevenueAllocator.Confidence.HIDDEN; // "ALL"
            };
        }
        // Legacy: minConfidence=HIGH narrows to HIGH; absent ⇒ ALL but excludes HIDDEN.
        if ("HIGH".equalsIgnoreCase(req.getMinConfidence())) {
            return c == RevenueAllocator.Confidence.HIGH;
        }
        return c != RevenueAllocator.Confidence.HIDDEN;
    }

    private void sortRows(List<JobProfitabilityRowDto> rows, String sortBy) {
        if (sortBy == null) sortBy = "PROFIT_DESC";
        switch (sortBy.toUpperCase()) {
            case "PROFIT_ASC" -> rows.sort(Comparator.comparing(JobProfitabilityRowDto::getProfit));
            case "MARGIN_DESC" -> rows.sort(Comparator.comparing(JobProfitabilityRowDto::getMarginPct).reversed());
            case "MARGIN_ASC" -> rows.sort(Comparator.comparing(JobProfitabilityRowDto::getMarginPct));
            case "REVENUE_DESC" -> rows.sort(Comparator.comparing(JobProfitabilityRowDto::getRevenue).reversed());
            case "INVOICE_DESC" -> rows.sort((a, b) -> compareInvoiceNo(b.getInvoiceNumber(), a.getInvoiceNumber()));
            case "INVOICE_ASC" -> rows.sort((a, b) -> compareInvoiceNo(a.getInvoiceNumber(), b.getInvoiceNumber()));
            default -> rows.sort(Comparator.comparing(JobProfitabilityRowDto::getProfit).reversed());
        }
    }

    /**
     * Compare two invoice numbers in Comarch ERP XL format like "FS/142/04/2026".
     * Sort key is (year, month, number) so months and years are chronological,
     * not lexicographic ("FS/2" comes before "FS/100" within the same period).
     * Nulls and unparsable values sort last.
     */
    static int compareInvoiceNo(String a, String b) {
        int[] ka = parseInvoiceKey(a);
        int[] kb = parseInvoiceKey(b);
        for (int i = 0; i < 3; i++) {
            int c = Integer.compare(ka[i], kb[i]);
            if (c != 0) return c;
        }
        // Tie-break alphabetically on the prefix to keep stable ordering when the
        // numeric tuple is identical (e.g. FS vs FSKOR with same number/year).
        String pa = a == null ? "" : a;
        String pb = b == null ? "" : b;
        return pa.compareTo(pb);
    }

    /** {year, month, number} — zeros if any part can't be parsed. */
    private static int[] parseInvoiceKey(String s) {
        if (s == null || s.isEmpty()) return new int[]{0, 0, 0};
        String[] parts = s.split("/");
        if (parts.length < 4) return new int[]{0, 0, 0};
        int n = parts.length;
        return new int[]{
                parseIntSafe(parts[n - 1]),  // year
                parseIntSafe(parts[n - 2]),  // month
                parseIntSafe(parts[n - 3])   // number
        };
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private static BigDecimal sum(List<JobProfitabilityRowDto> rows,
                                  java.util.function.Function<JobProfitabilityRowDto, BigDecimal> getter) {
        return rows.stream().map(r -> getter.apply(r) == null ? BigDecimal.ZERO : getter.apply(r))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static class WorkerAggregator {
        final String workerId;
        final String resourceId;
        BigDecimal totalMinutes = BigDecimal.ZERO;
        BigDecimal attributedRevenue = BigDecimal.ZERO;
        BigDecimal attributedMaterialCost = BigDecimal.ZERO;
        BigDecimal attributedLaborCost = BigDecimal.ZERO;
        int jobCount = 0;
        WorkerAggregator(String w, String r) { this.workerId = w; this.resourceId = r; }
    }
}
