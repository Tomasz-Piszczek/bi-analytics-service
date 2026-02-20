package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.CtiZasobPrc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CtiZasobPrcRepository extends JpaRepository<CtiZasobPrc, Integer> {
}
