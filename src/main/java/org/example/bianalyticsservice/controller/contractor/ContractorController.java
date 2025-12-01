package org.example.bianalyticsservice.controller.contractor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.controller.contractor.dto.ContractorDto;
import org.example.bianalyticsservice.service.ContractorService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/contractors")
@RequiredArgsConstructor
public class ContractorController {
    
    private final ContractorService contractorService;
    
    @GetMapping
    public ResponseEntity<Page<ContractorDto>> findAllContractors(Pageable pageable) {
        log.info("[findAllContractors] Getting all contractors with pagination: {}", pageable);
        return ResponseEntity.ok(contractorService.findAllContractors(pageable));
    }
}