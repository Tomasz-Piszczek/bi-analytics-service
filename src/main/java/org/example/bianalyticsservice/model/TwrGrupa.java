package org.example.bianalyticsservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "TwrGrupy")
@Getter
@Setter
public class TwrGrupa {

    @Id
    @Column(name = "TG_ID")
    private Integer id;

    @Column(name = "TG_Kod", length = 50)
    private String code;

    @Column(name = "TG_Nazwa", length = 255)
    private String name;

    @Column(name = "TG_Opis", length = 500)
    private String description;
}