package org.example.bianalyticsservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "TwrGrupy", schema = "CDN")
@SQLRestriction("TwG_GIDTyp = -16")
@Getter
@Setter
public class TwrGrupa {

    @Id
    @Column(name = "TwG_TwGID")
    private Integer id;

    @Column(name = "TwG_Kod", length = 50)
    private String code;

    @Column(name = "TwG_Nazwa", length = 255)
    private String name;

    @Column(name = "TwG_GIDNumer")
    private Integer gidNumber;

    @Column(name = "TwG_GrONumer")
    private Integer parentId;

    @Column(name = "TwG_CzasModyfikacji")
    private Integer modificationTime;
}