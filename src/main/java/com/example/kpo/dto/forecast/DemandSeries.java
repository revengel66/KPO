package com.example.kpo.dto.forecast;

import java.time.LocalDate;
import java.util.List;

public record DemandSeries(Long productId,
                           LocalDate startDate,
                           LocalDate endDate,
                           List<DemandPoint> points,
                           boolean insufficientData) {

    public long totalDemand() {
        if (points == null) {
            return 0;
        }
        return points.stream().mapToLong(DemandPoint::quantity).sum();
    }
}
