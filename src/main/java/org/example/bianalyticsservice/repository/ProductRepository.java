package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.controller.product.dto.ProductDto;
import org.example.bianalyticsservice.model.Products;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Products, Integer> {
    
    @Query(value = """
            SELECT new org.example.bianalyticsservice.controller.product.dto.ProductDto(
                t.code, 
                t.name, 
                t.unitOfMeasure,
                z.purchaseDate, 
                z.quantity, 
                z.price
            )
            FROM Products t 
            LEFT JOIN t.quantities z 
            WHERE z.quantity > 0
            ORDER BY t.id, z.purchaseDate DESC
            """)
    Page<ProductDto> findAllProductsWithResources(Pageable pageable);
}