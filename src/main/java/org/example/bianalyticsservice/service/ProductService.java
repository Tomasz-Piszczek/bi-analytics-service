package org.example.bianalyticsservice.service;

import lombok.RequiredArgsConstructor;
import org.example.bianalyticsservice.controller.product.dto.ProductDto;
import org.example.bianalyticsservice.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    
    public Page<ProductDto> findAllProductsWithResources(Pageable pageable) {
        return productRepository.findAllProductsWithResources(pageable);
    }
}