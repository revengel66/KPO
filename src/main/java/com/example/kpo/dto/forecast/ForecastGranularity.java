package com.example.kpo.dto.forecast;

import java.util.Locale;

public enum ForecastGranularity {
    DAY,
    WEEK;

    public static ForecastGranularity fromString(String value) {
        if (value == null || value.isBlank()) {
            return DAY;
        }
        try {
            return ForecastGranularity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return DAY;
        }
    }
}
