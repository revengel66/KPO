package com.example.kpo.dto.forecast;

import java.time.LocalDate;

public record DemandPoint(LocalDate date, long quantity) {
}
