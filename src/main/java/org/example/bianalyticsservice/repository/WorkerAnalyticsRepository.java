package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.CtiZlecenieNag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkerAnalyticsRepository extends JpaRepository<CtiZlecenieNag, Integer> {

    @Query(value = """
        SELECT
            czn.CZN_ID                           AS id,
            czn.CZN_NrPelny                      AS numer_zlecenia,
            CAST(czn.CZN_DataWystaw AS date)     AS date,
            t.Twr_Kod                            AS productTypeId,
            czn.CZN_Ilosc                        AS quantity,
            (
                SELECT
                    COALESCE(prc_single.CZ_Kod, cz2.CZ_Kod) AS workerId,
                    cz2.CZ_Kod AS resourceId,
                    CAST(zzs2.ZZs_DataOd AS date) AS workDate,
                    zzs2.ZZs_CzasMin AS minutesWorked,
                    zzs2.ZZs_DataOd AS timeFrom,
                    zzs2.ZZs_DataDo AS timeTo
                FROM dbo.CtiZlecenieZasob zzs2
                INNER JOIN dbo.CtiZasob cz2 ON zzs2.ZZs_CZID = cz2.CZ_ID
                LEFT JOIN (
                    SELECT zsp.ZsP_PrcId, MAX(cz_prc.CZ_Kod) AS CZ_Kod
                    FROM dbo.CtiZasobPrc zsp
                    INNER JOIN dbo.CtiZasob cz_prc ON zsp.ZsP_CZID = cz_prc.CZ_ID
                    GROUP BY zsp.ZsP_PrcId
                    HAVING COUNT(DISTINCT zsp.ZsP_CZID) = 1
                ) prc_single ON prc_single.ZsP_PrcId = zzs2.ZZs_PrcId
                WHERE zzs2.ZZs_CZNID = czn.CZN_ID
                ORDER BY COALESCE(prc_single.CZ_Kod, cz2.CZ_Kod), zzs2.ZZs_DataOd
                FOR JSON PATH
            )                                    AS workers,
            (
                SELECT SUM(zzs_inner.ZZs_CzasMin)
                FROM dbo.CtiZlecenieZasob zzs_inner
                WHERE zzs_inner.ZZs_CZNID = czn.CZN_ID
            )                                    AS totalMinutes,
            (
                SELECT pw.TrN_NumerPelny AS dokNumer,
                       e.TrE_TwrKod     AS twrKod,
                       e.TrE_Ilosc      AS ilosc,
                       e.TrE_WartoscNetto AS wartoscNetto
                FROM dbo.CtiZlecenieDok czd
                         JOIN CDN.TraNag pw ON pw.TrN_TrNID = czd.CZD_TrnId
                    AND pw.TrN_NumerPelny LIKE 'RW/%'
                    AND pw.TrN_Anulowany = 0
                         JOIN CDN.TraElem e ON e.TrE_TrNId = pw.TrN_TrNID
                WHERE czd.CZD_CZNId = czn.CZN_ID
                FOR JSON PATH
            )                                    AS rw_elements,
            (
                SELECT SUM(e.TrE_WartoscNetto)
                FROM dbo.CtiZlecenieDok czd
                         JOIN CDN.TraNag pw ON pw.TrN_TrNID = czd.CZD_TrnId
                    AND pw.TrN_NumerPelny LIKE 'RW/%'
                    AND pw.TrN_Anulowany = 0
                         JOIN CDN.TraElem e ON e.TrE_TrNId = pw.TrN_TrNID
                WHERE czd.CZD_CZNId = czn.CZN_ID
            )                                    AS rw_suma,
            (
                SELECT pw.TrN_NumerPelny AS dokNumer,
                       e.TrE_TwrKod     AS twrKod,
                       e.TrE_Ilosc      AS ilosc,
                       e.TrE_WartoscNetto AS wartoscNetto
                FROM dbo.CtiZlecenieDok czd
                         JOIN CDN.TraNag pw ON pw.TrN_TrNID = czd.CZD_TrnId
                    AND pw.TrN_NumerPelny LIKE 'PW/%'
                    AND pw.TrN_Anulowany = 0
                         JOIN CDN.TraElem e ON e.TrE_TrNId = pw.TrN_TrNID
                WHERE czd.CZD_CZNId = czn.CZN_ID
                FOR JSON PATH
            )                                    AS pw_elements,
            (
                SELECT SUM(e.TrE_WartoscNetto)
                FROM dbo.CtiZlecenieDok czd
                         JOIN CDN.TraNag pw ON pw.TrN_TrNID = czd.CZD_TrnId
                    AND pw.TrN_NumerPelny LIKE 'PW/%'
                    AND pw.TrN_Anulowany = 0
                         JOIN CDN.TraElem e ON e.TrE_TrNId = pw.TrN_TrNID
                WHERE czd.CZD_CZNId = czn.CZN_ID
            )                                    AS pw_suma
        FROM dbo.CtiZlecenieNag czn
                 INNER JOIN CDN.Towary t ON czn.CZN_TwrId = t.Twr_TwrId
                 INNER JOIN dbo.CtiZlecenieZasob zzs ON zzs.ZZs_CZNID = czn.CZN_ID
        GROUP BY czn.CZN_ID, czn.CZN_NrPelny, czn.CZN_DataWystaw, t.Twr_Kod, czn.CZN_Ilosc
        ORDER BY CAST(czn.CZN_DataWystaw AS date), czn.CZN_ID
        """, nativeQuery = true)
    List<Object[]> findWorkerAnalytics();
}
