package com.example.kpo.repository;

import com.example.kpo.entity.Movement;
import com.example.kpo.entity.MovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovementRepository extends JpaRepository<Movement, Long> {

    List<Movement> findByType(MovementType type);

    @Query("""
            SELECT DISTINCT m FROM Movement m
            LEFT JOIN FETCH m.items i
            LEFT JOIN FETCH i.product p
            LEFT JOIN FETCH p.category
            LEFT JOIN FETCH m.warehouse
            LEFT JOIN FETCH m.targetWarehouse
            WHERE (:untilDate IS NULL OR m.date <= :untilDate)
            """)
    List<Movement> findAllForReport(@Param("untilDate") LocalDateTime untilDate);
}
