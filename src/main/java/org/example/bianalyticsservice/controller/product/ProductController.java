package org.example.bianalyticsservice.controller.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bianalyticsservice.controller.product.dto.ProductDto;
import org.example.bianalyticsservice.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    
    private final ProductService productService;
    
    @GetMapping
    public ResponseEntity<Page<ProductDto>> findAllProducts(Pageable pageable, @RequestParam(defaultValue = "true") boolean filterQuantity) {
        log.info("[findAllProducts] Getting all products with pagination: {} and filterQuantity: {}", pageable, filterQuantity);
        return ResponseEntity.ok(productService.findAllProductsWithResources(pageable, filterQuantity));
    }
}