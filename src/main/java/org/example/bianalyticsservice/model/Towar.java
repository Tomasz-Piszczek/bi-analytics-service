package org.example.bianalyticsservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "Towary")
@Getter
@Setter
public class Towar {

    @Id
    @Column(name = "TW_ID")
    private Integer id;

    @Column(name = "TW_Kod", length = 50)
    private String code;

    @Column(name = "TW_Nazwa", length = 255)
    private String name;

    @Column(name = "TW_JM", length = 20)
    private String unitOfMeasure;

    @Column(name = "TW_GrupaId")
    private Integer groupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TW_GrupaId", insertable = false, updatable = false)
    private TwrGrupa group;
}