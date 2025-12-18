package com.example.kpo.repository;

import com.example.kpo.entity.MovementProduct;
import com.example.kpo.entity.MovementType;
import com.example.kpo.entity.Product;
import com.example.kpo.repository.projection.DailyDemandAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovementProductRepository extends JpaRepository<MovementProduct, Long> {

    boolean existsByProduct(Product product);

    @Query(value = """
            SELECT DATE(m.date) AS date,
                   SUM(mp.quantity) AS quantity
            FROM products_movement mp
            JOIN movements m ON mp.movement_id = m.id
            WHERE mp.product_id = :productId
              AND m.type = :movementType
              AND DATE(m.date) BETWEEN :from AND :to
            GROUP BY DATE(m.date)
            ORDER BY DATE(m.date)
            """, nativeQuery = true)
    List<DailyDemandAggregate> findDailyDemand(@Param("productId") Long productId,
                                               @Param("movementType") String movementType,
                                               @Param("from") String from,
                                               @Param("to") String to);

    @Query("""
            SELECT MAX(m.date)
            FROM MovementProduct mp
            JOIN mp.movement m
            WHERE mp.product = :product
              AND m.type = :movementType
            """)
    LocalDateTime findLastMovementDate(@Param("product") Product product,
                                       @Param("movementType") MovementType movementType);
}
