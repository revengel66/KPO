package com.example.kpo.dto.forecast;

import java.time.LocalDate;

public record ForecastPoint(LocalDate date, double value) {
}
