package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.TwrGrupa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TwrGrupaRepository extends JpaRepository<TwrGrupa, Integer> {
    
    @Query(value = """
            SELECT g.TwG_TwGID, g.TwG_Kod, g.TwG_Nazwa, g.TwG_GIDNumer, g.TwG_CzasModyfikacji
            FROM CDN.TwrGrupy g
            LEFT JOIN CDN.Towary t ON t.Twr_TwGGIDNumer = g.TwG_TwGID
            GROUP BY g.TwG_TwGID, g.TwG_Kod, g.TwG_Nazwa, g.TwG_GIDNumer, g.TwG_CzasModyfikacji
            HAVING COUNT(t.Twr_TwrId) > 0
            ORDER BY g.TwG_Kod
            """, nativeQuery = true)
    List<TwrGrupa> findGroupsWithProducts();
}