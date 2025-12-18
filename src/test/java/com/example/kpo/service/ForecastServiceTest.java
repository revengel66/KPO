package com.example.kpo.service;

import com.example.kpo.dto.forecast.DemandPoint;
import com.example.kpo.dto.forecast.DemandSeries;
import com.example.kpo.dto.forecast.ForecastGranularity;
import com.example.kpo.dto.forecast.ForecastResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock
    private DemandSeriesService demandSeriesService;

    private ForecastService forecastService;

    @BeforeEach
    void setUp() {
        forecastService = new ForecastService(demandSeriesService);
    }

    @Test
    void forecastProductReturnsForecastAndMetrics() {
        List<DemandPoint> history = buildHistory(10);
        DemandSeries series = new DemandSeries(
                5L,
                history.get(0).date(),
                history.get(history.size() - 1).date(),
                history,
                false
        );
        when(demandSeriesService.loadDailyDemandSeries(5L, 60)).thenReturn(series);

        ForecastResult result = forecastService.forecastProduct(5L, 60, 3, 2);

        assertThat(result.insufficientData()).isFalse();
        assertThat(result.forecast()).hasSize(2);
        assertThat(result.metrics()).isNotNull();
        assertThat(result.metrics().evaluatedPoints()).isGreaterThan(0);
        assertThat(result.forecast().get(0).date()).isEqualTo(history.get(history.size() - 1).date().plusDays(1));
    }

    @Test
    void forecastProductMarksInsufficientWhenHistoryTooShort() {
        List<DemandPoint> history = buildHistory(3);
        DemandSeries series = new DemandSeries(
                9L,
                history.get(0).date(),
                history.get(history.size() - 1).date(),
                history,
                false
        );
        when(demandSeriesService.loadDailyDemandSeries(9L, 30)).thenReturn(series);

        ForecastResult result = forecastService.forecastProduct(9L, 30, 3, 5);

        assertThat(result.insufficientData()).isTrue();
        assertThat(result.forecast()).isEmpty();
        verify(demandSeriesService).loadDailyDemandSeries(9L, 30);
    }

    @Test
    void forecastProductAggregatesWeeklyHistory() {
        int historyWeeks = 6;
        int dailyDays = historyWeeks * 7;
        LocalDate start = LocalDate.of(2024, 1, 1);
        List<DemandPoint> dailyHistory = java.util.stream.IntStream.range(0, dailyDays)
                .mapToObj(i -> new DemandPoint(start.plusDays(i), 1))
                .toList();
        DemandSeries dailySeries = new DemandSeries(
                5L,
                start,
                start.plusDays(dailyDays - 1),
                dailyHistory,
                false
        );
        when(demandSeriesService.loadDailyDemandSeries(5L, dailyDays)).thenReturn(dailySeries);

        ForecastResult result = forecastService.forecastProduct(5L, historyWeeks, 2, 2, ForecastGranularity.WEEK);

        assertThat(result.history()).hasSize(historyWeeks);
        assertThat(result.history().get(0).date()).isEqualTo(start);
        assertThat(result.history()).allSatisfy(point -> assertThat(point.quantity()).isEqualTo(7L));
        assertThat(result.forecast()).hasSize(2);
        LocalDate lastWeek = start.plusWeeks(historyWeeks - 1);
        assertThat(result.forecast().get(0).date()).isEqualTo(lastWeek.plusWeeks(1));
        verify(demandSeriesService).loadDailyDemandSeries(5L, dailyDays);
    }

    private List<DemandPoint> buildHistory(int days) {
        LocalDate start = LocalDate.of(2024, 1, 1);
        return java.util.stream.IntStream.range(0, days)
                .mapToObj(i -> new DemandPoint(start.plusDays(i), 10 + i))
                .toList();
    }
}
