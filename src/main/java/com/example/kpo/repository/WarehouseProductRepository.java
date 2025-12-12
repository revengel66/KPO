package com.example.kpo.repository;

import com.example.kpo.entity.Product;
import com.example.kpo.entity.Warehouse;
import com.example.kpo.entity.WarehouseProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseProductRepository extends JpaRepository<WarehouseProduct, Long> {

    Optional<WarehouseProduct> findByWarehouseAndProduct(Warehouse warehouse, Product product);

    List<WarehouseProduct> findByWarehouse(Warehouse warehouse);
}
