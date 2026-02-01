package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.TwrGrupa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface TwrGrupaRepository extends JpaRepository<TwrGrupa, Integer> {

    @Query(value = """
            WITH GroupHierarchy AS (
                SELECT
                    TwG_TwGID,
                    TwG_GIDNumer,
                    TwG_GrONumer,
                    TwG_Kod,
                    TwG_Nazwa,
                    0 as Level,
                    CAST(TwG_Kod as VARCHAR(500)) as Path
                FROM CDN.TwrGrupy
                WHERE TwG_GIDTyp = -16 AND TwG_GrONumer = 0

                UNION ALL

                SELECT
                    g.TwG_TwGID,
                    g.TwG_GIDNumer,
                    g.TwG_GrONumer,
                    g.TwG_Kod,
                    g.TwG_Nazwa,
                    h.Level + 1,
                    CAST(h.Path + ' > ' + g.TwG_Kod as VARCHAR(500))
                FROM CDN.TwrGrupy g
                INNER JOIN GroupHierarchy h ON g.TwG_GrONumer = h.TwG_GIDNumer
                WHERE g.TwG_GIDTyp = -16
            )
            SELECT
                TwG_TwGID as id,
                TwG_GIDNumer as gidNumber,
                TwG_GrONumer as parentId,
                TwG_Kod as code,
                TwG_Nazwa as name,
                Level as level,
                Path as path
            FROM GroupHierarchy
            ORDER BY Path
            """, nativeQuery = true)
    List<Map<String, Object>> findAllGroupsHierarchical();
}