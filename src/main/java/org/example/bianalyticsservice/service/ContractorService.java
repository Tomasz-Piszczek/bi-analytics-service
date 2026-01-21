package org.example.bianalyticsservice.service;

import lombok.RequiredArgsConstructor;
import org.example.bianalyticsservice.controller.contractor.dto.ContractorDto;
import org.example.bianalyticsservice.repository.ContractorRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContractorService {
    
    private final ContractorRepository contractorRepository;
    
    public List<ContractorDto> findAllContractors() {
        return contractorRepository.findAll().stream()
                .map(contractor -> new ContractorDto(contractor.getCode(), contractor.getName()))
                .collect(Collectors.toList());
    }
}