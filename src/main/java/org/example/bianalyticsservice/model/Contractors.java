package org.example.bianalyticsservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "Kontrahenci", schema = "CDN")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Contractors {

    @Id
    @Column(name = "Knt_KntId", nullable = false)
    private Integer id;

    @Column(name = "Knt_Kod", length = 20, nullable = false)
    private String code;

    @Column(name = "Knt_Nazwa1", length = 50, nullable = false)
    private String name;
}