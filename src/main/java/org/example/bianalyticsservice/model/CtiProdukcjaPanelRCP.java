package org.example.bianalyticsservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "CtiProdukcjaPanelRCP")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CtiProdukcjaPanelRCP {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "IDPracownika")
    private Integer idPracownika;

    @Column(name = "KodPracownika")
    private String kodPracownika;

    @Column(name = "DataOperacji")
    private LocalDateTime dataOperacji;

    @Column(name = "Typ")
    private Integer typ;
}
