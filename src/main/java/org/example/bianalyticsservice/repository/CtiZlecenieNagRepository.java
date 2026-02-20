package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.CtiZlecenieNag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CtiZlecenieNagRepository extends JpaRepository<CtiZlecenieNag, Integer> {

    @Query(value = """
        SELECT
            czn.CZN_NrPelny AS orderFullNumber,
            czn.CZN_TwrKod AS productCode,
            cz.CZ_Kod AS resourceCode,
            SUM(zzs.ZZs_CzasMin) AS totalMinutes
        FROM dbo.CtiZlecenieNag czn
        INNER JOIN dbo.CtiZlecenieZasob zzs ON czn.CZN_ID = zzs.ZZs_CZNID
        INNER JOIN dbo.CtiZasob cz ON zzs.ZZs_CZID = cz.CZ_ID
        GROUP BY czn.CZN_NrPelny, czn.CZN_TwrKod, cz.CZ_Kod
        ORDER BY czn.CZN_NrPelny, totalMinutes DESC
        """, nativeQuery = true)
    List<Object[]> findOrderResourceSummary();
}
