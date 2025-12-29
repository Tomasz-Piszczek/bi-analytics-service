package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.Towar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TowarRepository extends JpaRepository<Towar, Integer> {

    @Query("SELECT t FROM Towar t WHERE " +
           "(:groupId IS NULL OR t.groupId = :groupId)")
    Page<Towar> findByGroupId(@Param("groupId") Integer groupId, Pageable pageable);
}