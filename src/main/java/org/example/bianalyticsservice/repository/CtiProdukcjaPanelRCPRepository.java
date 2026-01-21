package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.CtiProdukcjaPanelRCP;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
    Double getHoursWorkedByEmployeeAndYearAndMonth(
            @Param("czKod") String czKod,
            @Param("year") Integer year,
            @Param("month") Integer month
    );
}
