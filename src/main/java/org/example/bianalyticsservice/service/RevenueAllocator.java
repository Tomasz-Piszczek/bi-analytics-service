package org.example.bianalyticsservice.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.example.bianalyticsservice.controller.analytics.dto.InvoiceLineDto;
import org.example.bianalyticsservice.controller.analytics.dto.InvoiceLinkDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Pure-function revenue allocator: given a job's product code, its quantity, the linked invoice,
 * and a map of "how many units of each product code are produced by the cohort of jobs sharing
 * this RO", compute the revenue attributable to this job.
 *
 * Tier 1 (HIGH): exact product-code match between the ZP's full product code (resolved from
 * CDN.Towary, NOT the truncated CZN_TwrKod) and an FS line.TwrKod.
 *   revenue = SUM over matching FS lines of  line.WartoscNetto * (job_qty / sibling_qty_for_code)
 *           - this ZP's proportional share of any FSKOR correction
 *
 * No match (NO_INVOICE): the ZP has no matching line on this FS. The earlier "Tier 2" equal-split
 * fallback is gone — it was inventing revenue for ZPs whose product was never billed on the FS
 * the chain happened to attach them to. Such ZPs now report revenue=0 and confidence=NO_INVOICE,
 * which the FE renders as "Bez faktury".
 */
@Component
public class RevenueAllocator {

    public enum Confidence {
        /** ZP's full product code matches an FS line — revenue computed precisely. */
        HIGH,
        /** ZP has a linked FS in the date window but no line on it matches the product code. */
        NO_INVOICE,
        /** ZP has no FS link reachable in the date window at all (or no link anywhere). */
        HIDDEN
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class Allocation {
        private BigDecimal revenue;
        private Confidence confidence;
    }

    /**
     * @param jobProductCode   ZP.CZN_TwrKod
     * @param jobQuantity      ZP.CZN_Ilosc
     * @param link             invoice link (FS lines + co-bundle count + FSKOR net)
     * @param siblingQtyByCode map productCode → SUM(CZN_Ilosc) across all ZPs in the same RO
     *                         that share this productCode (built once per RO outside)
     */
    public Allocation allocate(String jobProductCode,
                               BigDecimal jobQuantity,
                               InvoiceLinkDto link,
                               Map<String, BigDecimal> siblingQtyByCode) {
        if (link == null || link.getFsLines() == null || link.getFsLines().isEmpty()
                || jobProductCode == null) {
            return Allocation.builder().revenue(BigDecimal.ZERO).confidence(Confidence.NO_INVOICE).build();
        }
        BigDecimal qty = jobQuantity == null || jobQuantity.signum() <= 0 ? BigDecimal.ONE : jobQuantity;

        List<InvoiceLineDto> matching = link.getFsLines().stream()
                .filter(l -> l.getTwrKod() != null && l.getTwrKod().equals(jobProductCode))
                .toList();
        if (matching.isEmpty()) {
            return Allocation.builder().revenue(BigDecimal.ZERO).confidence(Confidence.NO_INVOICE).build();
        }

        BigDecimal siblingQty = siblingQtyByCode.getOrDefault(jobProductCode, qty);
        if (siblingQty.signum() <= 0) siblingQty = qty;
        BigDecimal revenue = BigDecimal.ZERO;
        for (InvoiceLineDto line : matching) {
            BigDecimal lineNet = nz(line.getWartoscNetto());
            revenue = revenue.add(lineNet.multiply(qty).divide(siblingQty, 4, RoundingMode.HALF_UP));
        }
        revenue = applyFsKorrShare(revenue, link, qty, siblingQty, /*equal*/ false);
        return Allocation.builder().revenue(revenue.setScale(2, RoundingMode.HALF_UP)).confidence(Confidence.HIGH).build();
    }

    /** Subtract this job's share of the FSKOR (correction) net amount. */
    private BigDecimal applyFsKorrShare(BigDecimal baseRevenue,
                                         InvoiceLinkDto link,
                                         BigDecimal jobQty,
                                         BigDecimal divisor,
                                         boolean equal) {
        BigDecimal kor = nz(link.getFsKorrectionNet());
        if (kor.signum() == 0 || divisor.signum() <= 0) return baseRevenue;
        BigDecimal share = equal
                ? kor.divide(divisor, 4, RoundingMode.HALF_UP)
                : kor.multiply(jobQty).divide(divisor, 4, RoundingMode.HALF_UP);
        return baseRevenue.subtract(share);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
