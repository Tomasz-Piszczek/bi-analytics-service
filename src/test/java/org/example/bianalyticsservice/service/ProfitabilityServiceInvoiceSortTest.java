package org.example.bianalyticsservice.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies invoice-number comparison sorts by (year, month, number) chronologically,
 * not lexicographically (so "FS/2/04/2026" comes BEFORE "FS/100/04/2026" within the
 * same period, and "FS/.../12/2025" comes BEFORE "FS/.../01/2026").
 */
class ProfitabilityServiceInvoiceSortTest {

    @Test
    void sortsWithinSameYearMonthByNumber() {
        // ASC order should put low number first
        assertThat(ProfitabilityService.compareInvoiceNo("FS/2/04/2026", "FS/100/04/2026"))
                .isNegative();
        assertThat(ProfitabilityService.compareInvoiceNo("FS/100/04/2026", "FS/2/04/2026"))
                .isPositive();
    }

    @Test
    void monthBoundary_dec2025_beforeJan2026() {
        assertThat(ProfitabilityService.compareInvoiceNo("FS/300/12/2025", "FS/1/01/2026"))
                .isNegative();
    }

    @Test
    void differentYears() {
        assertThat(ProfitabilityService.compareInvoiceNo("FS/142/04/2026", "FS/142/04/2025"))
                .isPositive();
    }

    @Test
    void equalsItself() {
        assertThat(ProfitabilityService.compareInvoiceNo("FS/142/04/2026", "FS/142/04/2026"))
                .isZero();
    }

    @Test
    void nullsSortAsZero_butStableOnPrefixTieBreak() {
        // Two nulls compare equal
        assertThat(ProfitabilityService.compareInvoiceNo(null, null)).isZero();
        // Null vs unparsable empty string both yield {0,0,0}; tie-break alphabetically
        int c = ProfitabilityService.compareInvoiceNo(null, "");
        // "" > null per the tie-breaker: null treated as "" → equal
        assertThat(c).isZero();
    }

    @Test
    void unparseable_doesNotThrow() {
        // Garbage values should produce a result (any), not exception
        ProfitabilityService.compareInvoiceNo("garbage", "FS/1/01/2026");
        ProfitabilityService.compareInvoiceNo("FS/1/01/2026", "garbage");
    }

    @Test
    void fsAndFskor_sameNumberAndPeriod_sortByPrefix() {
        // FS comes before FSKOR alphabetically (tie-break)
        assertThat(ProfitabilityService.compareInvoiceNo("FS/142/04/2026", "FSKOR/142/04/2026"))
                .isNegative();
    }
}
