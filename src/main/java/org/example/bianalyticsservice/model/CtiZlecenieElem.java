package org.example.bianalyticsservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "CtiZlecenieElem", schema = "dbo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CtiZlecenieElem {

    @Id
    @Column(name = "CZE_ID")
    private Integer id;

    @Column(name = "CZE_CZNId")
    private Integer orderHeaderId;

    @Column(name = "CZE_CZNTyp")
    private Integer orderHeaderType;

    @Column(name = "CZE_Lp")
    private Integer lineNumber;

    @Column(name = "CZE_TwrId")
    private Integer productId;

    @Column(name = "CZE_TwrKod")
    private String productCode;

    @Column(name = "CZE_Ilosc")
    private BigDecimal quantity;

    @Column(name = "CZE_Typ")
    private Integer type;

    @Column(name = "CZE_CenaWaga")
    private BigDecimal priceWeight;

    @Column(name = "CZE_Cena")
    private BigDecimal price;

    @Column(name = "CZE_DokId")
    private Integer documentId;

    @Column(name = "CZE_DokTyp")
    private Integer documentType;

    @Column(name = "CZE_DokNumer")
    private String documentNumber;

    @Column(name = "CZE_Opis")
    private String description;

    @Column(name = "CZE_Data")
    private LocalDateTime date;

    @Column(name = "CZE_Magazyn")
    private Integer warehouse;

    @Column(name = "CZE_WplywNaCene")
    private Integer priceImpact;

    @Column(name = "CZE_Staly")
    private Boolean fixed;

    @Column(name = "CZE_DzialID")
    private Integer departmentId;

    @Column(name = "CZE_Polprodukt")
    private Boolean semiProduct;
}
