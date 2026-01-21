package org.example.bianalyticsservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "Towary", schema = "CDN")
@Getter
@Setter
public class Towar {

    @Id
    @Column(name = "Twr_TwrId")
    private Integer id;

    @Column(name = "Twr_Kod", length = 50)
    private String code;

    @Column(name = "Twr_Nazwa", length = 255)
    private String name;

    @Column(name = "Twr_JM", length = 20)
    private String unitOfMeasure;

    @Column(name = "Twr_TwGGIDNumer")
    private Integer groupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Twr_TwGGIDNumer", referencedColumnName = "TwG_TwGID", insertable = false, updatable = false)
    private TwrGrupa group;
}