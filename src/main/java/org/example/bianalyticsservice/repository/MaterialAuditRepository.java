package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.CtiZlecenieNag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MaterialAuditRepository extends JpaRepository<CtiZlecenieNag, Integer> {

    @Query(value = """
        SELECT
            czn.CZN_ID                          AS orderId,
            czn.CZN_NrPelny                     AS numerZlecenia,
            CAST(czn.CZN_DataWystaw AS DATE)    AS dataZlecenia,
            t.Twr_Kod                           AS productTypeId,
            tw_elem.Twr_Kod                     AS expectedTwrKod,
            (elem.CZE_Ilosc * czn.CZN_Ilosc)   AS expectedIlosc
        FROM dbo.CtiZlecenieNag czn
        JOIN CDN.Towary t               ON t.Twr_TwrId = czn.CZN_TwrId
        LEFT JOIN dbo.CtiZlecenieElem elem
                                        ON elem.CZE_CZNId = czn.CZN_ID
                                        AND elem.CZE_Typ = 2
        LEFT JOIN CDN.Towary tw_elem    ON tw_elem.Twr_TwrId = elem.CZE_TwrId
        WHERE CAST(czn.CZN_DataWystaw AS DATE) BETWEEN :dateFrom AND :dateTo
        ORDER BY czn.CZN_ID
        """, nativeQuery = true)
    List<Object[]> findExpectedMaterials(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo
    );

    @Query(value = """
        SELECT
            czn.CZN_ID          AS orderId,
            cz.CZ_Kod           AS pracownik,
            e.TrE_TwrKod        AS actualTwrKod,
            e.TrE_Ilosc         AS actualIlosc
        FROM dbo.CtiZlecenieNag czn
        LEFT JOIN dbo.CtiZlecenieDok czd    ON czd.CZD_CZNId = czn.CZN_ID
        LEFT JOIN CDN.TraNag pw             ON pw.TrN_TrNID = czd.CZD_TrnId
                                            AND pw.TrN_NumerPelny LIKE 'RW/%'
                                            AND pw.TrN_Anulowany = 0
        LEFT JOIN CDN.TraElem e             ON e.TrE_TrNId = pw.TrN_TrNID
        LEFT JOIN dbo.CtiZasobPrc zsp       ON zsp.ZsP_PrcId = czd.CZD_PracownikId
        LEFT JOIN dbo.CtiZasob cz           ON cz.CZ_ID = zsp.ZsP_CZID
                                            AND cz.CZ_Kod != 'Produkcja'
        WHERE CAST(czn.CZN_DataWystaw AS DATE) BETWEEN :dateFrom AND :dateTo
        ORDER BY czn.CZN_ID
        """, nativeQuery = true)
    List<Object[]> findActualMaterials(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo
    );
}