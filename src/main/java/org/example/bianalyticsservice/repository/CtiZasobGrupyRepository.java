package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.CtiZasobGrupy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CtiZasobGrupyRepository extends JpaRepository<CtiZasobGrupy, Integer> {
    
    Optional<CtiZasobGrupy> findByCode(String code);
}