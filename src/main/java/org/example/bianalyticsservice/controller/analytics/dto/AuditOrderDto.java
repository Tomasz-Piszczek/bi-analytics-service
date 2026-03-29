package org.example.bianalyticsservice.controller.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditOrderDto {
    private String numerZlecenia;
    private LocalDate dataZlecenia;
    private String productTypeId;
    private List<AuditMaterialEntryDto> materials;
    private boolean orderOk;
    private List<WorkerTimeEntryDto> workerTimeEntries;
}
