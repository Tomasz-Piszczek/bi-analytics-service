package org.example.bianalyticsservice.service;

import lombok.RequiredArgsConstructor;
import org.example.bianalyticsservice.controller.product.dto.ProductDto;
import org.example.bianalyticsservice.controller.product.dto.ProductGroupDto;
import org.example.bianalyticsservice.repository.ProductRepository;
import org.example.bianalyticsservice.repository.TwrGrupaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    private final TwrGrupaRepository twrGrupaRepository;
    

    public Page<ProductDto> findAllProductsWithResources(Pageable pageable, boolean filterQuantity, Integer groupId) {
        return productRepository.findAllProductsWithResourcesByGroup(pageable, filterQuantity, groupId);
    }
    
    public List<ProductGroupDto> findAllGroups() {
        return twrGrupaRepository.findAllByOrderByName().stream()
                .map(group -> ProductGroupDto.builder()
                        .id(group.getId())
                        .code(group.getCode())
                        .name(group.getName())
                        .description(group.getDescription())
                        .build())
                .collect(Collectors.toList());
    }
}