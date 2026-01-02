package org.example.bianalyticsservice.service;

import lombok.RequiredArgsConstructor;
import org.example.bianalyticsservice.controller.product.dto.ProductDto;
import org.example.bianalyticsservice.controller.product.dto.ProductGroupDto;
import org.example.bianalyticsservice.repository.ProductRepository;
import org.example.bianalyticsservice.repository.TwrGrupaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    private final TwrGrupaRepository twrGrupaRepository;
    

    public List<ProductDto> findAllProductsWithResources(boolean filterQuantity, Integer groupId) {
        return productRepository.findAllProductsWithResourcesByGroup(filterQuantity, groupId);
    }
    
    public List<ProductGroupDto> findAllGroups() {
        return twrGrupaRepository.findGroupsWithProducts().stream()
                .map(group -> ProductGroupDto.builder()
                        .id(group.getGidNumber())
                        .code(group.getCode())
                        .name(group.getCode())
                        .build())
                .collect(Collectors.toList());
    }
}