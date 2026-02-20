package org.example.bianalyticsservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "CtiZlecenieNag", schema = "dbo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CtiZlecenieNag {

    @Id
    @Column(name = "CZN_ID")
    private Integer id;

    @Column(name = "CZN_Numer")
    private Integer number;

    @Column(name = "CZN_Miesiac")
    private Integer month;

    @Column(name = "CZN_Rok")
    private Integer year;

    @Column(name = "CZN_Seria")
    private String series;

    @Column(name = "CZN_NrPelny")
    private String fullNumber;

    @Column(name = "CZN_CTNId")
    private Integer ctnId;

    @Column(name = "CZN_TwrId")
    private Integer productId;

    @Column(name = "CZN_TwrKod")
    private String productCode;

    @Column(name = "CZN_Ilosc")
    private BigDecimal quantity;

    @Column(name = "CZN_Status")
    private Integer status;

    @Column(name = "CZN_OpeWystaw")
    private Integer issuingOperator;

    @Column(name = "CZN_DataWystaw")
    private LocalDateTime issueDate;

    @Column(name = "CZN_OperModyf")
    private Integer modifyingOperator;

    @Column(name = "CZN_DataModyf")
    private LocalDateTime modifyDate;

    @Column(name = "CZN_DokZwiazID")
    private Integer relatedDocId;

    @Column(name = "CZN_DokZwiazTreID")
    private Integer relatedDocContentId;

    @Column(name = "CZN_DokZwiazTyp")
    private Integer relatedDocType;

    @Column(name = "CZN_DataRealizacji")
    private LocalDateTime realizationDate;

    @Column(name = "CZN_Opis")
    private String description;

    @Column(name = "CZN_CzasStart")
    private LocalDateTime startTime;

    @Column(name = "CZN_CzasEnd")
    private LocalDateTime endTime;

    @Column(name = "CZN_PominCzasZasobow")
    private Integer skipResourceTime;

    @Column(name = "CZN_TwrJm")
    private String productUnit;

    @Column(name = "CZN_LP")
    private Integer lp;

    @Column(name = "CZN_CenaID")
    private Integer priceId;

    @Column(name = "CZN_DomyslneMagSur")
    private Boolean defaultWarehouseRaw;

    @Column(name = "CZN_NrOpakowania")
    private Integer packagingNumber;

    @Column(name = "CZN_CZZNIdNadrzedne")
    private Integer parentOrderId;

    @Column(name = "CZN_CZZNNrPelnyNadrzedne")
    private String parentFullNumber;

    @Column(name = "CZN_CZZNIdGlowne")
    private Integer mainOrderId;

    @Column(name = "CZN_CZZNNrPelnyGlowne")
    private String mainFullNumber;

    @Column(name = "CZN_Licznik")
    private Integer counter;

    @Column(name = "CZN_UwzglednijDateZPNaZasobach")
    private Integer considerProductionDateOnResources;
}
