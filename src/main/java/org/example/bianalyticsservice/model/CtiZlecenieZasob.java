package org.example.bianalyticsservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "CtiZlecenieZasob")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CtiZlecenieZasob {
    
    @Id
    @Column(name = "ZZs_ID")
    private Integer id;
    
    @Column(name = "ZZs_CZNID")
    private Integer orderNumber;
    
    @Column(name = "ZZs_CZID")
    private Integer resourceId;
    
    @Column(name = "ZZs_CTNID")
    private Integer ctnId;
    
    @Column(name = "ZZs_PrcId")
    private Integer processId;
    
    @Column(name = "ZZs_DataOd")
    private LocalDateTime dateFrom;
    
    @Column(name = "ZZs_DataDo")
    private LocalDateTime dateTo;
    
    @Column(name = "ZZs_CzasMin")
    private BigDecimal timeMinutes;
    
    @Column(name = "ZZs_PrcKod", length = 40)
    private String processCode;
    
    @Column(name = "ZZs_Ilosc")
    private BigDecimal quantity;
    
    @Column(name = "ZZs_Data")
    private LocalDateTime date;
    
    @Column(name = "ZZs_OpeId")
    private Integer operatorId;
    
    @Column(name = "ZZs_Opis", length = 4000)
    private String description;
    
    @Column(name = "ZZs_ProcentRealizacji")
    private BigDecimal realizationPercent;
    
    @Column(name = "ZZs_Zakonczono")
    private Boolean completed;
    
    @Column(name = "ZZs_DokTyp")
    private Integer documentType;
    
    @Column(name = "ZZs_Rozliczono")
    private Boolean settled;
    
    @Column(name = "ZZs_StworzonoAwarie")
    private Boolean failureCreated;
    
    @Column(name = "ZZs_StworzonoPW")
    private Boolean pwCreated;
    
    @Column(name = "ZZs_StworzonoRW")
    private Boolean rwCreated;
    
    @Column(name = "ZZs_CZSID")
    private Integer czsId;
}