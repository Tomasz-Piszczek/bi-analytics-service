package org.example.bianalyticsservice.service;

import lombok.RequiredArgsConstructor;
import org.example.bianalyticsservice.controller.contractor.dto.ContractorDto;
import org.example.bianalyticsservice.repository.ContractorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContractorService {
    
    private final ContractorRepository contractorRepository;
    
    public Page<ContractorDto> findAllContractors(Pageable pageable) {
        return contractorRepository.findAll(pageable)
                .map(contractor -> new ContractorDto(contractor.getCode(), contractor.getName()));
    }
}