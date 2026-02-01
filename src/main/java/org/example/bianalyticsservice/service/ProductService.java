package org.example.bianalyticsservice.service;

import lombok.RequiredArgsConstructor;
import org.example.bianalyticsservice.controller.product.dto.ProductDto;
import org.example.bianalyticsservice.controller.product.dto.ProductGroupDto;
import org.example.bianalyticsservice.repository.ProductRepository;
import org.example.bianalyticsservice.repository.TwrGrupaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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
        return twrGrupaRepository.findAllGroupsHierarchical().stream()
                .map(this::mapToProductGroupDto)
                .collect(Collectors.toList());
    }

    private ProductGroupDto mapToProductGroupDto(Map<String, Object> row) {
        return ProductGroupDto.builder()
                .id((Integer) row.get("gidNumber"))
                .code((String) row.get("code"))
                .name((String) row.get("name"))
                .parentId((Integer) row.get("parentId"))
                .level((Integer) row.get("level"))
                .path((String) row.get("path"))
                .build();
    }
}