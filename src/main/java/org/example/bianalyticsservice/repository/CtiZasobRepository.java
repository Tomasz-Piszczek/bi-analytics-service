package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.CtiZasob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CtiZasobRepository extends JpaRepository<CtiZasob, Integer> {
    
    @Query("SELECT cz FROM CtiZasob cz " +
           "JOIN CtiZasobGrupy czg ON cz.groupId = czg.id " +
           "WHERE czg.code = :groupCode")
    List<CtiZasob> findByGroupCode(@Param("groupCode") String groupCode);
}