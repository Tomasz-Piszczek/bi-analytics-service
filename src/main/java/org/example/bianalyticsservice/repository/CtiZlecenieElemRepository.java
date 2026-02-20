package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.CtiZlecenieElem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CtiZlecenieElemRepository extends JpaRepository<CtiZlecenieElem, Integer> {
}
