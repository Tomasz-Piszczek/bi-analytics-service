package org.example.bianalyticsservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "CtiZasobPrc")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CtiZasobPrc {

    @Id
    @Column(name = "ZsP_ID")
    private Integer id;

    @Column(name = "ZsP_CZID")
    private Integer czId;

    @Column(name = "ZsP_PrcId")
    private Integer prcId;

    @Column(name = "ZsP_PrcKod")
    private String prcKod;

    @Column(name = "ZsP_Ilosc")
    private BigDecimal ilosc;

    @ManyToOne
    @JoinColumn(name = "ZsP_CZID", referencedColumnName = "CZ_ID", insertable = false, updatable = false)
    private CtiZasob zasob;
}
