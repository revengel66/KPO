package com.example.kpo.repository;

import com.example.kpo.entity.Product;
import com.example.kpo.entity.Warehouse;
import com.example.kpo.entity.WarehouseProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseProductRepository extends JpaRepository<WarehouseProduct, Long> {

    Optional<WarehouseProduct> findByWarehouseAndProduct(Warehouse warehouse, Product product);

    List<WarehouseProduct> findByWarehouse(Warehouse warehouse);

    boolean existsByProduct(Product product);

    @Query("""
            SELECT wp FROM WarehouseProduct wp
            JOIN FETCH wp.warehouse w
            JOIN FETCH wp.product p
            LEFT JOIN FETCH p.category c
            WHERE (:warehouseFilter = false OR w.id IN :warehouseIds)
              AND (:categoryFilter = false OR c.id IN :categoryIds)
            """)
    List<WarehouseProduct> findForReport(@Param("warehouseIds") List<Long> warehouseIds,
                                         @Param("warehouseFilter") boolean warehouseFilter,
                                         @Param("categoryIds") List<Long> categoryIds,
                                         @Param("categoryFilter") boolean categoryFilter);
}
