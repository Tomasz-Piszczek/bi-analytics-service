package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.controller.product.dto.ProductDto;
import org.example.bianalyticsservice.model.Products;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
                z.price,
                new org.example.bianalyticsservice.controller.product.dto.ProductGroupDto(
                    g.id,
                    g.code,
                    g.name,
                    g.description
                )
            )
            FROM Products t 
            LEFT JOIN t.quantities z 
            LEFT JOIN t.group g
            WHERE (:filterQuantity = false OR z.quantity > 0)
            AND (:groupId IS NULL OR t.groupId = :groupId)
            ORDER BY t.id, z.purchaseDate DESC
            """)
    Page<ProductDto> findAllProductsWithResourcesByGroup(Pageable pageable, @Param("filterQuantity") boolean filterQuantity, @Param("groupId") Integer groupId);
}