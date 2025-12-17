package com.example.kpo.repository;

import com.example.kpo.entity.MovementProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MovementProductRepository extends JpaRepository<MovementProduct, Long> {

    boolean existsByProductId(Long productId);
}
