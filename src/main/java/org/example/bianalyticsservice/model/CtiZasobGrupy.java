package org.example.bianalyticsservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "CtiZasobGrupy")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CtiZasobGrupy {
    
    @Id
    @Column(name = "CZG_ID")
    private Integer id;
    
    @Column(name = "CZG_Kod", length = 20)
    private String code;
    
    @Column(name = "CZG_Lp")
    private Integer orderNumber;
}