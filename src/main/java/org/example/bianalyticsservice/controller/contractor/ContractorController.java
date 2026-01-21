package org.example.bianalyticsservice.controller.contractor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.controller.contractor.dto.ContractorDto;
import org.example.bianalyticsservice.service.ContractorService;
import java.util.List;
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
    public ResponseEntity<List<ContractorDto>> findAllContractors() {
        log.info("[findAllContractors] Getting all contractors");
        return ResponseEntity.ok(contractorService.findAllContractors());
    }
}