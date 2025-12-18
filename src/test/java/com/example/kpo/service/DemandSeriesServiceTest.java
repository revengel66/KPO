package com.example.kpo.service;

import com.example.kpo.dto.forecast.DemandSeries;
import com.example.kpo.entity.MovementType;
import com.example.kpo.entity.Product;
import com.example.kpo.repository.MovementProductRepository;
import com.example.kpo.repository.ProductRepository;
import com.example.kpo.repository.projection.DailyDemandAggregate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandSeriesServiceTest {

    @Mock
    private MovementProductRepository movementProductRepository;

    @Mock
    private ProductRepository productRepository;

    private DemandSeriesService demandSeriesService;

    private Product product;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2024-02-01T00:00:00Z"), ZoneId.of("UTC"));
        demandSeriesService = new DemandSeriesService(movementProductRepository, productRepository, clock);
        product = new Product();
        product.setId(7L);
    }

    @Test
    void loadDailyDemandSeriesFillsMissingDays() {
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
        LocalDateTime lastDate = LocalDateTime.of(2024, 1, 10, 12, 0);
        when(movementProductRepository.findLastMovementDate(product, MovementType.OUTBOUND)).thenReturn(lastDate);
        List<DailyDemandAggregate> aggregates = List.of(
                aggregate(LocalDate.of(2024, 1, 9), 5L),
                aggregate(LocalDate.of(2024, 1, 10), 3L)
        );
        when(movementProductRepository.findDailyDemand(
                ArgumentMatchers.eq(product.getId()),
                ArgumentMatchers.eq(MovementType.OUTBOUND.name()),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()
        )).thenReturn(aggregates);

        DemandSeries series = demandSeriesService.loadDailyDemandSeries(7L, 5);

        assertThat(series.startDate()).isEqualTo(LocalDate.of(2024, 1, 6));
        assertThat(series.endDate()).isEqualTo(LocalDate.of(2024, 1, 10));
        assertThat(series.points()).hasSize(5);
        assertThat(series.points().get(0).quantity()).isZero();
        assertThat(series.points().get(3).quantity()).isEqualTo(5L);
        assertThat(series.points().get(4).quantity()).isEqualTo(3L);
        assertThat(series.totalDemand()).isEqualTo(8L);
        assertThat(series.insufficientData()).isFalse();
    }

    @Test
    void loadDailyDemandSeriesFallsBackToClockWhenNoData() {
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
        when(movementProductRepository.findLastMovementDate(product, MovementType.OUTBOUND)).thenReturn(null);
        when(movementProductRepository.findDailyDemand(
                ArgumentMatchers.eq(product.getId()),
                ArgumentMatchers.eq(MovementType.OUTBOUND.name()),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()
        )).thenReturn(List.of());

        DemandSeries series = demandSeriesService.loadDailyDemandSeries(7L, 4);

        assertThat(series.endDate()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(series.startDate()).isEqualTo(LocalDate.of(2024, 1, 29));
        assertThat(series.points()).allSatisfy(point -> assertThat(point.quantity()).isZero());
        assertThat(series.insufficientData()).isTrue();
    }

    private DailyDemandAggregate aggregate(LocalDate date, Long quantity) {
        return new DailyDemandAggregate() {
            @Override
            public LocalDate getDate() {
                return date;
            }

            @Override
            public Long getQuantity() {
                return quantity;
            }
        };
    }
}
