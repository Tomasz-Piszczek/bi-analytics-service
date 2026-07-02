package org.example.bianalyticsservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Per-production-order resource/operation plan row — the source of the
 * "Harmonogram / Grafik produkcji" feature.
 *
 * One row = a resource (a named worker from the {@code Pracownicy} group, or an
 * operation/machine like {@code Malowanie}) assigned to a production order
 * ({@code CZS_CTNID} -> {@code CtiZlecenieNag.CZN_ID}) with a planned time window
 * ({@code CZS_CzasStart}/{@code CZS_CzasEnd}) and a planned duration
 * ({@code CZS_CzasPracy}; unit in {@code CZS_JMCzasu}, where 2 = hours).
 *
 * Only a subset of columns is mapped — the ones the grafik reads/writes.
 */
@Entity
@Table(name = "CtiZasobDok", schema = "dbo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CtiZasobDok {

    @Id
    @Column(name = "CZS_ID")
    private Integer id;

    @Column(name = "CZS_CZID")
    private Integer resourceId;

    @Column(name = "CZS_CTNID")
    private Integer orderCtnId;

    @Column(name = "CZS_Kod", length = 50)
    private String code;

    @Column(name = "CZS_JMCzasu")
    private Integer timeUnit;

    /** Assigned work duration. The value the grafik edits (in {@link #timeUnit} units; 2 = hours). */
    @Column(name = "CZS_CzasPracy")
    private BigDecimal workTime;

    @Column(name = "CZS_CzasStart")
    private LocalDateTime startTime;

    @Column(name = "CZS_CzasEnd")
    private LocalDateTime endTime;

    @Column(name = "CZS_Ilosc")
    private BigDecimal quantity;

    @Column(name = "CZS_PrcId")
    private Integer employeeId;

    @Column(name = "CZS_ZasobPrcId")
    private Integer resourceEmployeeId;

    @Column(name = "CZS_Zakonczono")
    private Integer finished;

    @Column(name = "CZS_DokTyp")
    private Integer docType;

    @Column(name = "CZS_CzasPlanowany")
    private BigDecimal plannedTime;

    @Column(name = "CZS_WieleOsob")
    private Integer multiplePeople;
}
