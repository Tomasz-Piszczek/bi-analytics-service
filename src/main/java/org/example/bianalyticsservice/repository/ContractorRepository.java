package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.Contractors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractorRepository extends JpaRepository<Contractors, Integer> {
}