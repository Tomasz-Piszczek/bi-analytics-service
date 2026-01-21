package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.CtiZlecenieZasob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CtiZlecenieZasobRepository extends JpaRepository<CtiZlecenieZasob, Integer> {
    
    @Query("SELECT SUM(czz.timeMinutes) FROM CtiZlecenieZasob czz " +
           "WHERE czz.resourceId = :resourceId " +
           "AND czz.date >= :startDate AND czz.date <= :endDate")
    BigDecimal sumWorkMinutesByResourceIdAndDateRange(
            @Param("resourceId") Integer resourceId, 
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT czz FROM CtiZlecenieZasob czz " +
           "WHERE czz.resourceId = :resourceId " +
           "AND czz.date >= :startDate AND czz.date <= :endDate")
    List<CtiZlecenieZasob> findByResourceIdAndDateRange(
            @Param("resourceId") Integer resourceId, 
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate);
}