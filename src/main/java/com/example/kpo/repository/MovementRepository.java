package com.example.kpo.repository;

import com.example.kpo.entity.Movement;
import com.example.kpo.entity.MovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovementRepository extends JpaRepository<Movement, Long> {

    List<Movement> findByType(MovementType type);
}
