package com.example.kpo.dto.forecast;

import java.util.List;

public record ForecastResult(Long productId,
                             List<DemandPoint> history,
                             List<ForecastPoint> forecast,
                             ForecastMetrics metrics,
                             double alpha,
                             double beta,
                             boolean insufficientData,
                             String model) {
}
