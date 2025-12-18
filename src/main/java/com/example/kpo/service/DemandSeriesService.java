package com.example.kpo.service;

import com.example.kpo.dto.forecast.DemandPoint;
import com.example.kpo.dto.forecast.DemandSeries;
import com.example.kpo.entity.MovementType;
import com.example.kpo.entity.Product;
import com.example.kpo.repository.MovementProductRepository;
import com.example.kpo.repository.ProductRepository;
import com.example.kpo.repository.projection.DailyDemandAggregate;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DemandSeriesService {

    public static final int DEFAULT_HISTORY_DAYS = 60;

    private final MovementProductRepository movementProductRepository;
    private final ProductRepository productRepository;
    private final Clock clock;

    public DemandSeriesService(MovementProductRepository movementProductRepository,
                               ProductRepository productRepository,
                               Clock clock) {
        this.movementProductRepository = movementProductRepository;
        this.productRepository = productRepository;
        this.clock = clock;
    }

    public DemandSeries loadDailyDemandSeries(Long productId) {
        return loadDailyDemandSeries(productId, DEFAULT_HISTORY_DAYS);
    }

    public DemandSeries loadDailyDemandSeries(Long productId, int historyDays) {
        if (historyDays <= 0) {
            throw new IllegalArgumentException("History window must be greater than 0");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        LocalDate endDate = determineEndDate(product);
        LocalDate startDate = historyDays == 1 ? endDate : endDate.minusDays(historyDays - 1);

        List<DailyDemandAggregate> rawData = Optional.ofNullable(
                movementProductRepository.findDailyDemand(
                        product.getId(),
                        MovementType.OUTBOUND.name(),
                        startDate.toString(),
                        endDate.toString()
                )
        ).orElse(Collections.emptyList());

        Map<LocalDate, Long> aggregatedByDate = new HashMap<>();
        for (DailyDemandAggregate aggregate : rawData) {
            if (aggregate == null || aggregate.getDate() == null) {
                continue;
            }
            long quantity = aggregate.getQuantity() == null ? 0L : aggregate.getQuantity();
            aggregatedByDate.merge(aggregate.getDate(), quantity, Long::sum);
        }

        List<DemandPoint> points = new ArrayList<>(historyDays);
        LocalDate pointer = startDate;
        while (!pointer.isAfter(endDate)) {
            long quantity = aggregatedByDate.getOrDefault(pointer, 0L);
            points.add(new DemandPoint(pointer, quantity));
            pointer = pointer.plusDays(1);
        }

        boolean insufficientData = rawData.isEmpty();
        return new DemandSeries(product.getId(), startDate, endDate, List.copyOf(points), insufficientData);
    }

    private LocalDate determineEndDate(Product product) {
        LocalDateTime lastMovementDate = movementProductRepository.findLastMovementDate(product, MovementType.OUTBOUND);
        if (lastMovementDate != null) {
            return lastMovementDate.toLocalDate();
        }
        return LocalDate.now(clock);
    }
}
