package org.example.bianalyticsservice.service;

import org.example.bianalyticsservice.controller.analytics.dto.InvoiceLineDto;
import org.example.bianalyticsservice.controller.analytics.dto.InvoiceLinkDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RevenueAllocatorTest {

    private final RevenueAllocator allocator = new RevenueAllocator();

    @Test
    void exact_match_singleZpInRo_takesFullLine() {
        InvoiceLinkDto link = link(1, List.of(line("RAMA", "1500.00")));
        var result = allocator.allocate("RAMA", new BigDecimal("1"), link, Map.of("RAMA", new BigDecimal("1")));
        assertThat(result.getRevenue()).isEqualByComparingTo("1500.00");
        assertThat(result.getConfidence()).isEqualTo(RevenueAllocator.Confidence.HIGH);
    }

    @Test
    void exact_match_realKabinaCase_16zpsSplitEqualByQuantity() {
        // RO 78490 in live DB: 16x KABINA LOGO each qty=1, FS line "KABINA LOGO x16, 3360.00"
        InvoiceLinkDto link = link(16, List.of(line("KABINA LOGO", "3360.00")));
        Map<String, BigDecimal> sibling = Map.of("KABINA LOGO", new BigDecimal("16"));

        var r = allocator.allocate("KABINA LOGO", new BigDecimal("1"), link, sibling);
        assertThat(r.getRevenue()).isEqualByComparingTo("210.00");
        assertThat(r.getConfidence()).isEqualTo(RevenueAllocator.Confidence.HIGH);
    }

    @Test
    void exact_match_quantityWeighted_oneZpQty2VsOthersQty1() {
        // 5 ZPs total (1 with qty=2, 4 with qty=1) of same product. Sibling sum = 6.
        InvoiceLinkDto link = link(5, List.of(line("PRODUCT-A", "1200.00")));
        Map<String, BigDecimal> sibling = Map.of("PRODUCT-A", new BigDecimal("6"));

        var r1 = allocator.allocate("PRODUCT-A", new BigDecimal("2"), link, sibling);
        var r2 = allocator.allocate("PRODUCT-A", new BigDecimal("1"), link, sibling);
        // qty=2 ZP gets 2/6 = 400; qty=1 ZP gets 1/6 = 200
        assertThat(r1.getRevenue()).isEqualByComparingTo("400.00");
        assertThat(r2.getRevenue()).isEqualByComparingTo("200.00");
    }

    @Test
    void exact_match_multiProductInvoice_onlyOwnLineCounts() {
        InvoiceLinkDto link = link(7, List.of(
                line("KRONE",   "7500.00"),
                line("WYWROTKA","4000.00"),
                line("TRANSPORT","200.00")
        ));
        Map<String, BigDecimal> sibling = Map.of("KRONE", new BigDecimal("5"), "WYWROTKA", new BigDecimal("2"));

        var rKrone = allocator.allocate("KRONE", new BigDecimal("1"), link, sibling);
        var rWyw   = allocator.allocate("WYWROTKA", new BigDecimal("1"), link, sibling);

        assertThat(rKrone.getRevenue()).isEqualByComparingTo("1500.00");      // 7500 / 5
        assertThat(rWyw.getRevenue()).isEqualByComparingTo("2000.00");        // 4000 / 2
        // TRANSPORT was not attributed to either job — that's intentional
    }

    @Test
    void noMatch_returnsZeroRevenueWithNoInvoiceConfidence() {
        // The old equal-split fallback was inventing revenue for ZPs that weren't actually
        // billed by this FS. The new behavior is honest: no matching FS line → 0 zł, NO_INVOICE.
        InvoiceLinkDto link = link(3, List.of(
                line("ASSEMBLY-PART-A", "5000.00"),
                line("ASSEMBLY-PART-B", "3000.00")
        ));
        var r = allocator.allocate("UNRELATED-CODE", new BigDecimal("1"), link, Map.of());
        assertThat(r.getRevenue()).isEqualByComparingTo("0");
        assertThat(r.getConfidence()).isEqualTo(RevenueAllocator.Confidence.NO_INVOICE);
    }

    @Test
    void emptyFsLines_returnsNoInvoice() {
        InvoiceLinkDto link = link(1, List.of());
        var r = allocator.allocate("RAMA", new BigDecimal("1"), link, Map.of());
        assertThat(r.getRevenue()).isEqualByComparingTo("0");
        assertThat(r.getConfidence()).isEqualTo(RevenueAllocator.Confidence.NO_INVOICE);
    }

    @Test
    void fskor_correction_isNetted() {
        InvoiceLinkDto link = link(1, List.of(line("RAMA", "1500.00")));
        link.setFsKorrectionNet(new BigDecimal("-200.00"));   // returns/discounts

        var r = allocator.allocate("RAMA", new BigDecimal("1"), link, Map.of("RAMA", new BigDecimal("1")));
        // 1500 - (-200 * 1/1) = 1500 - (-200) = 1700? No — the FSKOR is already negative for a return.
        // Actually corrections net to whatever signed value. Subtracting a negative ADDS, but a real return
        // would have a positive net (it's the credit amount). Test both signs explicitly:
        assertThat(r.getRevenue()).isEqualByComparingTo("1700.00");

        link.setFsKorrectionNet(new BigDecimal("200.00"));    // positive correction = customer reduced
        var r2 = allocator.allocate("RAMA", new BigDecimal("1"), link, Map.of("RAMA", new BigDecimal("1")));
        assertThat(r2.getRevenue()).isEqualByComparingTo("1300.00");
    }

    @Test
    void emptyLink_returnsZero() {
        var r = allocator.allocate("RAMA", new BigDecimal("1"), null, Map.of());
        assertThat(r.getRevenue()).isEqualByComparingTo("0");
    }

    private InvoiceLinkDto link(int coCount, List<InvoiceLineDto> lines) {
        return InvoiceLinkDto.builder()
                .coBundledZpCount(coCount)
                .fsLines(lines)
                .fsKorrectionNet(BigDecimal.ZERO)
                .build();
    }

    private InvoiceLineDto line(String code, String netto) {
        return InvoiceLineDto.builder()
                .twrKod(code)
                .ilosc(BigDecimal.ONE)
                .wartoscNetto(new BigDecimal(netto))
                .build();
    }
}
