package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.CtiProdukcjaPanelRCP;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface CtiProdukcjaPanelRCPRepository extends JpaRepository<CtiProdukcjaPanelRCP, Integer> {

    @Query(value = """
            WITH WorkPairs AS (
                SELECT
                    IDPracownika,
                    DataOperacji AS StartTime,
                    LEAD(DataOperacji) OVER (PARTITION BY IDPracownika ORDER BY DataOperacji) AS EndTime,
                    Typ
                FROM CtiProdukcjaPanelRCP
            ),
            WorkedHours AS (
                SELECT
                    IDPracownika,
                    StartTime,
                    EndTime,
                    DATEDIFF(MINUTE, StartTime, EndTime) / 60.0 AS HoursWorked
                FROM WorkPairs
                WHERE Typ = 1
                  AND EndTime IS NOT NULL
            )
            SELECT
                SUM(wh.HoursWorked)
            FROM WorkedHours wh
            JOIN CtiZasobPrc zp ON wh.IDPracownika = zp.ZsP_PrcId
            JOIN CtiZasob z ON zp.ZsP_CZID = z.CZ_ID
            WHERE z.CZ_Kod = :czKod
              AND YEAR(wh.StartTime) = :year
              AND MONTH(wh.StartTime) = :month
            """,
            nativeQuery = true)
    BigDecimal getHoursWorkedByEmployeeAndYearAndMonth(
            @Param("czKod") String czKod,
            @Param("year") Integer year,
            @Param("month") Integer month
    );

    @Query(value = """
            WITH WorkPairs AS (
                SELECT
                    IDPracownika,
                    DataOperacji AS StartTime,
                    LEAD(DataOperacji) OVER (PARTITION BY IDPracownika ORDER BY DataOperacji) AS EndTime,
                    Typ
                FROM CtiProdukcjaPanelRCP
            ),
            WorkedHours AS (
                SELECT
                    IDPracownika,
                    CAST(StartTime AS DATE) AS WorkDate,
                    StartTime,
                    EndTime,
                    DATEDIFF(MINUTE, StartTime, EndTime) / 60.0 AS HoursWorked
                FROM WorkPairs
                WHERE Typ = 1
                  AND EndTime IS NOT NULL
            )
            SELECT
                wh.WorkDate AS date,
                SUM(wh.HoursWorked) AS hours,
                MIN(wh.StartTime) AS startTime,
                MAX(wh.EndTime) AS endTime
            FROM WorkedHours wh
            JOIN CtiZasobPrc zp ON wh.IDPracownika = zp.ZsP_PrcId
            JOIN CtiZasob z ON zp.ZsP_CZID = z.CZ_ID
            WHERE z.CZ_Kod = :czKod
              AND YEAR(wh.StartTime) = :year
              AND MONTH(wh.StartTime) = :month
            GROUP BY wh.WorkDate
            ORDER BY wh.WorkDate
            """,
            nativeQuery = true)
    List<Object[]> getDailyHoursWorkedByEmployeeAndYearAndMonth(
            @Param("czKod") String czKod,
            @Param("year") Integer year,
            @Param("month") Integer month
    );
}
