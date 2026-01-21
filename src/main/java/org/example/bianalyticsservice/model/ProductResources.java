package org.example.bianalyticsservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Table(name = "TwrZasoby", schema = "CDN")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductResources {

    @Id
    @Column(name = "TwZ_TwZId", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TwZ_TwrId", referencedColumnName = "Twr_TwrId", nullable = false)
    private Products product;

    @Column(name = "TwZ_Ilosc", nullable = false)
    private BigDecimal quantity;

    @Column(name = "TwZ_Cena", nullable = false)
    private BigDecimal price;

    @Column(name = "TwZ_Data")
    private LocalDateTime purchaseDate;
}