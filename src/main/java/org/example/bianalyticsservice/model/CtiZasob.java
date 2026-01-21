package org.example.bianalyticsservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "CtiZasob")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CtiZasob {
    
    @Id
    @Column(name = "CZ_ID")
    private Integer id;
    
    @Column(name = "CZ_CZGID")
    private Integer groupId;
    
    @Column(name = "CZ_Kod", length = 50)
    private String code;
    
    @Column(name = "CZ_JMCzasu")
    private Integer timeUnit;
    
    @Column(name = "CZ_CzasPracy")
    private BigDecimal workTime;
    
    @Column(name = "CZ_CzasUzbrojenia")
    private BigDecimal setupTime;
    
    @Column(name = "CZ_CzasRozbrojenia")
    private BigDecimal teardownTime;
    
    @Column(name = "CZ_KosztPracy")
    private BigDecimal workCost;
    
    @Column(name = "CZ_KosztUzbrojenia")
    private BigDecimal setupCost;
    
    @Column(name = "CZ_KosztRozbrojenia")
    private BigDecimal teardownCost;
    
    @Column(name = "CZ_KontrolaDostepnosci")
    private Boolean availabilityControl;
    
    @Column(name = "CZ_RejestracjaCzasuPracy")
    private Boolean workTimeRegistration;
    
    @Column(name = "CZ_Dzielnik")
    private BigDecimal divisor;
    
    @Column(name = "CZ_Ilosc")
    private BigDecimal quantity;
    
    @Column(name = "CZ_Typ")
    private Integer type;
    
    @Column(name = "CZ_DostepnyOd")
    private LocalDateTime availableFrom;
    
    @Column(name = "CZ_DostepnyDo")
    private LocalDateTime availableTo;
    
    @Column(name = "CZ_DzialID")
    private Integer departmentId;
    
    @Column(name = "CZ_WieleOsob")
    private Integer multiplePeople;
    
    @Column(name = "CZ_CzasPlanowany")
    private Integer plannedTime;
    
    @Column(name = "CZ_RozbijanieCzasuZPZ")
    private Integer timeBreakdown;
    
    @Column(name = "CZ_PlanowanieCzasuPracy")
    private Integer workTimePlanning;
    
    @Column(name = "CZ_WykrywajKonflikty")
    private Boolean detectConflicts;
    
    @Column(name = "CZ_PrzeliczanieCzasu")
    private Boolean timeCalculation;
    
    @Column(name = "CZ_JednaRealizacja")
    private Boolean singleExecution;
    
    @Column(name = "CZ_ZwiekszajCzas")
    private Boolean increaseTime;
    
    @Column(name = "CZ_ZmniejszajCzas")
    private Boolean decreaseTime;
    
    @Column(name = "CZ_KolorA")
    private Integer colorA;
    
    @Column(name = "CZ_KolorR")
    private Integer colorR;
    
    @Column(name = "CZ_KolorG")
    private Integer colorG;
    
    @Column(name = "CZ_KolorB")
    private Integer colorB;
    
    @Column(name = "CZ_IloscDomyslna")
    private BigDecimal defaultQuantity;
}