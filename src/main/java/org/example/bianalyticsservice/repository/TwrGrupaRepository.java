package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.TwrGrupa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TwrGrupaRepository extends JpaRepository<TwrGrupa, Integer> {
    
    List<TwrGrupa> findAllByOrderByName();
}