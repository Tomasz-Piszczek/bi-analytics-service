package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.CtiZasobDok;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CtiZasobDokRepository extends JpaRepository<CtiZasobDok, Integer> {

    /**
     * The grafik for a date window: every named-worker ({@code Pracownicy} group)
     * assignment whose planned start falls on a day in [from, to].
     * {@code CZ_Kod} is cast to nvarchar to avoid CP1250 mojibake.
     */
    @Query(value = """
        SELECT
            d.CZS_ID                          AS czsId,
            CAST(cz.CZ_Kod AS nvarchar(50))   AS workerName,
            d.CZS_CZID                        AS resourceId,
            n.CZN_ID                          AS orderId,
            CAST(n.CZN_NrPelny AS nvarchar(40)) AS orderNumber,
            CAST(n.CZN_TwrKod  AS nvarchar(80)) AS productCode,
            n.CZN_Ilosc                       AS quantity,
            n.CZN_Status                      AS orderStatus,
            d.CZS_CzasPracy                   AS plannedHours,
            d.CZS_JMCzasu                     AS timeUnit,
            d.CZS_CzasStart                   AS startTime,
            d.CZS_CzasEnd                     AS endTime,
            d.CZS_Zakonczono                  AS finished,
            CAST(t.TrN_OdbNazwa1 AS nvarchar(120)) AS contractorName
        FROM dbo.CtiZasobDok d
        INNER JOIN dbo.CtiZasob cz       ON cz.CZ_ID = d.CZS_CZID
        INNER JOIN dbo.CtiZasobGrupy g   ON g.CZG_ID = cz.CZ_CZGID AND g.CZG_Kod = 'Pracownicy'
        INNER JOIN dbo.CtiZlecenieNag n  ON n.CZN_ID = d.CZS_CTNID
        LEFT JOIN CDN.TraNag t           ON t.TrN_TrNID = n.CZN_DokZwiazID
        WHERE CAST(d.CZS_CzasStart AS date) <= :to
          AND CAST(d.CZS_CzasEnd   AS date) >= :from
        ORDER BY d.CZS_CzasStart, workerName
        """, nativeQuery = true)
    List<Object[]> findGrafik(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * ACTUAL execution (rzeczywisty) for a date window — real logged work intervals
     * from dbo.CtiZlecenieZasob (the same source Analiza Pracowników uses). Column
     * order matches {@link #findGrafik} so the same row mapper/DTO is reused.
     * Worker is resolved with the analytics {@code prc_single} fallback
     * (COALESCE(prc_single.CZ_Kod, cz2.CZ_Kod)) and limited to the Pracownicy group.
     * Open sessions ({@code ZZs_DataDo IS NULL}) are excluded.
     */
    @Query(value = """
        SELECT
            z.ZZs_ID                                       AS czsId,
            CAST(COALESCE(prc_single.CZ_Kod, cz2.CZ_Kod) AS nvarchar(50)) AS workerName,
            cz2.CZ_ID                                      AS resourceId,
            n.CZN_ID                                       AS orderId,
            CAST(n.CZN_NrPelny AS nvarchar(40))            AS orderNumber,
            CAST(n.CZN_TwrKod  AS nvarchar(80))            AS productCode,
            n.CZN_Ilosc                                    AS quantity,
            n.CZN_Status                                   AS orderStatus,
            CAST(z.ZZs_CzasMin / 60.0 AS decimal(15,4))    AS plannedHours,
            2                                              AS timeUnit,
            z.ZZs_DataOd                                   AS startTime,
            z.ZZs_DataDo                                   AS endTime,
            z.ZZs_Zakonczono                               AS finished,
            CAST(t.TrN_OdbNazwa1 AS nvarchar(120))         AS contractorName
        FROM dbo.CtiZlecenieZasob z
        INNER JOIN dbo.CtiZasob cz2      ON z.ZZs_CZID = cz2.CZ_ID
        INNER JOIN dbo.CtiZlecenieNag n  ON n.CZN_ID = z.ZZs_CZNID
        LEFT JOIN CDN.TraNag t           ON t.TrN_TrNID = n.CZN_DokZwiazID
        LEFT JOIN (
            SELECT zsp.ZsP_PrcId, MAX(cz_prc.CZ_Kod) AS CZ_Kod
            FROM dbo.CtiZasobPrc zsp
            INNER JOIN dbo.CtiZasob cz_prc ON zsp.ZsP_CZID = cz_prc.CZ_ID
            GROUP BY zsp.ZsP_PrcId
            HAVING COUNT(DISTINCT zsp.ZsP_CZID) = 1
        ) prc_single ON prc_single.ZsP_PrcId = z.ZZs_PrcId
        WHERE z.ZZs_DataDo IS NOT NULL
          AND CAST(z.ZZs_DataOd AS date) <= :to
          AND CAST(z.ZZs_DataDo AS date) >= :from
          AND EXISTS (
              SELECT 1 FROM dbo.CtiZasob w
              INNER JOIN dbo.CtiZasobGrupy g ON g.CZG_ID = w.CZ_CZGID AND g.CZG_Kod = 'Pracownicy'
              WHERE w.CZ_Kod = COALESCE(prc_single.CZ_Kod, cz2.CZ_Kod)
          )
        ORDER BY z.ZZs_DataOd, workerName
        """, nativeQuery = true)
    List<Object[]> findGrafikActual(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Order header (number, product, qty, status, contractor) for the detail popover. */
    @Query(value = """
        SELECT
            CAST(n.CZN_NrPelny AS nvarchar(40))    AS orderNumber,
            CAST(n.CZN_TwrKod  AS nvarchar(80))    AS productCode,
            n.CZN_Ilosc                            AS quantity,
            n.CZN_Status                           AS orderStatus,
            CAST(t.TrN_OdbNazwa1 AS nvarchar(120)) AS contractorName
        FROM dbo.CtiZlecenieNag n
        LEFT JOIN CDN.TraNag t ON t.TrN_TrNID = n.CZN_DokZwiazID
        WHERE n.CZN_ID = :orderId
        """, nativeQuery = true)
    List<Object[]> findOrderHeader(@Param("orderId") Integer orderId);

    /** Who actually worked on an order and for how many minutes (from CtiZlecenieZasob). */
    @Query(value = """
        SELECT
            CAST(COALESCE(prc_single.CZ_Kod, cz2.CZ_Kod) AS nvarchar(50)) AS workerName,
            SUM(z.ZZs_CzasMin) AS minutes
        FROM dbo.CtiZlecenieZasob z
        INNER JOIN dbo.CtiZasob cz2 ON z.ZZs_CZID = cz2.CZ_ID
        LEFT JOIN (
            SELECT zsp.ZsP_PrcId, MAX(cz_prc.CZ_Kod) AS CZ_Kod
            FROM dbo.CtiZasobPrc zsp
            INNER JOIN dbo.CtiZasob cz_prc ON zsp.ZsP_CZID = cz_prc.CZ_ID
            GROUP BY zsp.ZsP_PrcId
            HAVING COUNT(DISTINCT zsp.ZsP_CZID) = 1
        ) prc_single ON prc_single.ZsP_PrcId = z.ZZs_PrcId
        WHERE z.ZZs_CZNID = :orderId
        GROUP BY COALESCE(prc_single.CZ_Kod, cz2.CZ_Kod)
        ORDER BY SUM(z.ZZs_CzasMin) DESC
        """, nativeQuery = true)
    List<Object[]> findOrderWorkers(@Param("orderId") Integer orderId);

    /** Materials issued to an order (RW documents), grouped by product code. */
    @Query(value = """
        SELECT
            CAST(e.TrE_TwrKod AS nvarchar(80)) AS twrKod,
            SUM(e.TrE_Ilosc)      AS ilosc,
            SUM(e.TrE_WartoscNetto) AS wartoscNetto
        FROM dbo.CtiZlecenieDok czd
        INNER JOIN CDN.TraNag pw  ON pw.TrN_TrNID = czd.CZD_TrnId
                                 AND pw.TrN_NumerPelny LIKE 'RW/%'
                                 AND pw.TrN_Anulowany = 0
        INNER JOIN CDN.TraElem e  ON e.TrE_TrNId = pw.TrN_TrNID
        WHERE czd.CZD_CZNId = :orderId
        GROUP BY CAST(e.TrE_TwrKod AS nvarchar(80))
        ORDER BY SUM(e.TrE_WartoscNetto) DESC
        """, nativeQuery = true)
    List<Object[]> findOrderMaterials(@Param("orderId") Integer orderId);

    /**
     * Free-text search over the grafik by order number ({@code CZN_NrPelny}) or
     * contractor name ({@code TrN_OdbNazwa1}). Same column shape as {@link #findGrafik}
     * so results can be mapped by the same row mapper. Ranked so prefix matches
     * ({@code :prefix}) come first, then the furthest-in-the-future plan date.
     * TOP-capped because a single order spans many worker/day rows.
     */
    @Query(value = """
        SELECT TOP 150
            d.CZS_ID                          AS czsId,
            CAST(cz.CZ_Kod AS nvarchar(50))   AS workerName,
            d.CZS_CZID                        AS resourceId,
            n.CZN_ID                          AS orderId,
            CAST(n.CZN_NrPelny AS nvarchar(40)) AS orderNumber,
            CAST(n.CZN_TwrKod  AS nvarchar(80)) AS productCode,
            n.CZN_Ilosc                       AS quantity,
            n.CZN_Status                      AS orderStatus,
            d.CZS_CzasPracy                   AS plannedHours,
            d.CZS_JMCzasu                     AS timeUnit,
            d.CZS_CzasStart                   AS startTime,
            d.CZS_CzasEnd                     AS endTime,
            d.CZS_Zakonczono                  AS finished,
            CAST(t.TrN_OdbNazwa1 AS nvarchar(120)) AS contractorName
        FROM dbo.CtiZasobDok d
        INNER JOIN dbo.CtiZasob cz       ON cz.CZ_ID = d.CZS_CZID
        INNER JOIN dbo.CtiZasobGrupy g   ON g.CZG_ID = cz.CZ_CZGID AND g.CZG_Kod = 'Pracownicy'
        INNER JOIN dbo.CtiZlecenieNag n  ON n.CZN_ID = d.CZS_CTNID
        LEFT JOIN CDN.TraNag t           ON t.TrN_TrNID = n.CZN_DokZwiazID
        WHERE n.CZN_NrPelny LIKE :like
           OR CAST(t.TrN_OdbNazwa1 AS nvarchar(120)) LIKE :like
        ORDER BY
            CASE WHEN n.CZN_NrPelny LIKE :prefix
                   OR LTRIM(REPLACE(CAST(t.TrN_OdbNazwa1 AS nvarchar(120)), '"', '')) LIKE :prefix
                 THEN 0 ELSE 1 END,
            d.CZS_CzasStart DESC
        """, nativeQuery = true)
    List<Object[]> searchGrafik(@Param("like") String like, @Param("prefix") String prefix);

    /**
     * Resource availability windows for the {@code Pracownicy} group on a given day,
     * from CtiZasobDostepny. One row per worker interval (workers have at most one
     * per day). A worker absent from the result is unavailable that day.
     * {@code ZsD_CalyDzien = 1} means the whole day is available.
     */
    @Query(value = """
        SELECT
            CAST(cz.CZ_Kod AS nvarchar(50)) AS workerName,
            d.ZsD_CzasOd                    AS fromTime,
            d.ZsD_CzasDo                    AS toTime,
            d.ZsD_CalyDzien                 AS wholeDay
        FROM dbo.CtiZasobDostepny d
        INNER JOIN dbo.CtiZasob cz     ON cz.CZ_ID = d.ZsD_CZID
        INNER JOIN dbo.CtiZasobGrupy g ON g.CZG_ID = cz.CZ_CZGID AND g.CZG_Kod = 'Pracownicy'
        WHERE d.ZsD_Data = :day
        """, nativeQuery = true)
    List<Object[]> findAvailability(@Param("day") LocalDate day);

    /**
     * Move/resize a single plan row: writes ONLY the planned time window
     * ({@code CZS_CzasStart}/{@code CZS_CzasEnd}). {@code CZS_CzasPracy} (the ERP
     * work-hours) and {@code CZS_JMCzasu} are intentionally left untouched — the
     * window and the work-hours are independent in the ERP, so we never overwrite
     * work-hours from the calendar span. Returns rows affected (0 if id missing).
     *
     * NOTE: requires UPDATE permission on dbo.CtiZasobDok for the app's DB user
     * (the default {@code myapp} login is db_datareader only — grant UPDATE on
     * this table, or db_datawriter, before this can succeed).
     */
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE dbo.CtiZasobDok
        SET CZS_CzasStart = :start,
            CZS_CzasEnd   = :end
        WHERE CZS_ID = :czsId
        """, nativeQuery = true)
    int updateWindow(@Param("czsId") Integer czsId,
                     @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);
}
