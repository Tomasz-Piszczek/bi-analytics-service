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
