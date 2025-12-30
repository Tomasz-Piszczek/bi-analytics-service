package org.example.bianalyticsservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "Towary", schema = "CDN")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Products {

    @Id
    @Column(name = "Twr_TwrId", nullable = false)
    private Integer id;

    @Column(name = "Twr_Kod", length = 50, nullable = false)
    private String code;

    @Column(name = "Twr_JM", length = 50, nullable = false)
    private String unitOfMeasure;

    @Column(name = "Twr_Nazwa", length = 255, nullable = false)
    private String name;

    @Column(name = "Twr_TwGGIDNumer")
    private Integer groupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Twr_TwGGIDNumer", referencedColumnName = "TwG_GIDNumer", insertable = false, updatable = false)
    private TwrGrupa group;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<ProductResources> quantities;
}