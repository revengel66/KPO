package com.example.kpo.service;

import com.example.kpo.dto.forecast.DemandPoint;
import com.example.kpo.dto.forecast.DemandSeries;
import com.example.kpo.dto.forecast.ForecastGranularity;
import com.example.kpo.dto.forecast.ForecastMetrics;
import com.example.kpo.dto.forecast.ForecastPoint;
import com.example.kpo.dto.forecast.ForecastResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ForecastService {

    private static final double[] ALPHA_GRID = {0.2, 0.4, 0.6, 0.8};
    private static final double[] BETA_GRID = {0.1, 0.2, 0.3, 0.4};
    private static final double[] CROSTON_ALPHA_GRID = {0.1, 0.2, 0.3, 0.4};
    private static final int MIN_HISTORY_POINTS = 5;
    private static final int MIN_NONZERO_POINTS = 2;
    private static final double INTERMITTENT_ZERO_RATIO = 0.4;
    private static final double INTERMITTENT_INTERVAL_THRESHOLD = 1.5;
    private static final String MODEL_HOLT = "HOLT";
    private static final String MODEL_CROSTON = "CROSTON";

    private final DemandSeriesService demandSeriesService;

    public ForecastService(DemandSeriesService demandSeriesService) {
        this.demandSeriesService = demandSeriesService;
    }

    public ForecastResult forecastProduct(Long productId,
                                          int historyDays,
                                          int validationWindow,
                                          int horizonDays) {
        return forecastProduct(productId, historyDays, validationWindow, horizonDays, ForecastGranularity.DAY);
    }

    public ForecastResult forecastProduct(Long productId,
                                          int historyDays,
                                          int validationWindow,
                                          int horizonDays,
                                          ForecastGranularity granularity) {
        if (horizonDays <= 0) {
            throw new IllegalArgumentException("Forecast horizon must be greater than 0");
        }
        int dailyHistoryDays = granularity == ForecastGranularity.WEEK
                ? historyDays * 7
                : historyDays;
        DemandSeries dailySeries = demandSeriesService.loadDailyDemandSeries(productId, dailyHistoryDays);
        DemandSeries series = granularity == ForecastGranularity.WEEK
                ? aggregateWeeklySeries(dailySeries, historyDays)
                : dailySeries;
        List<DemandPoint> history = series.points();
        IntermittencyStats intermittency = analyzeIntermittency(history);
        boolean insufficient = series.insufficientData()
                || history.size() < MIN_HISTORY_POINTS
                || intermittency.nonZeroCount() < MIN_NONZERO_POINTS;

        boolean useCroston = intermittency.isIntermittent();
        ForecastEvaluation evaluation = useCroston
                ? evaluateCrostonParameters(history, validationWindow)
                : evaluateHoltParameters(history, validationWindow);
        double alpha = evaluation.alpha();
        double beta = evaluation.beta();
        ForecastMetrics metrics = evaluation.metrics().orElse(null);

        List<ForecastPoint> forecastPoints = insufficient
                ? List.of()
                : (MODEL_CROSTON.equals(evaluation.model())
                    ? generateCrostonForecast(history, horizonDays, alpha, granularity)
                    : generateHoltForecast(history, horizonDays, alpha, beta, granularity));

        return new ForecastResult(
                productId,
                history,
                forecastPoints,
                metrics,
                alpha,
                beta,
                insufficient,
                evaluation.model()
        );
    }

    private ForecastEvaluation evaluateHoltParameters(List<DemandPoint> history, int validationWindow) {
        if (history.size() < MIN_HISTORY_POINTS) {
            return new ForecastEvaluation(MODEL_HOLT, ALPHA_GRID[0], BETA_GRID[0], Optional.empty());
        }
        int validationSize = Math.min(validationWindow, Math.max(1, history.size() / 4));
        int trainingSize = history.size() - validationSize;
        if (trainingSize < 2) {
            return new ForecastEvaluation(MODEL_HOLT, ALPHA_GRID[0], BETA_GRID[0], Optional.empty());
        }

        ForecastEvaluation best = null;
        for (double alpha : ALPHA_GRID) {
            for (double beta : BETA_GRID) {
                ForecastMetrics metrics = validateHoltCandidate(history, trainingSize, validationSize, alpha, beta);
                if (metrics == null) {
                    continue;
                }
                if (best == null || metrics.mae() < best.metrics().orElseThrow().mae()) {
                    best = new ForecastEvaluation(MODEL_HOLT, alpha, beta, Optional.of(metrics));
                }
            }
        }
        if (best == null) {
            return new ForecastEvaluation(MODEL_HOLT, ALPHA_GRID[0], BETA_GRID[0], Optional.empty());
        }
        return best;
    }

    private ForecastEvaluation evaluateCrostonParameters(List<DemandPoint> history, int validationWindow) {
        if (history.size() < MIN_HISTORY_POINTS) {
            return new ForecastEvaluation(MODEL_CROSTON, CROSTON_ALPHA_GRID[0], 0, Optional.empty());
        }
        int validationSize = Math.min(validationWindow, Math.max(1, history.size() / 4));
        int trainingSize = history.size() - validationSize;
        if (trainingSize < 2) {
            return new ForecastEvaluation(MODEL_CROSTON, CROSTON_ALPHA_GRID[0], 0, Optional.empty());
        }

        ForecastEvaluation best = null;
        for (double alpha : CROSTON_ALPHA_GRID) {
            ForecastMetrics metrics = validateCrostonCandidate(history, trainingSize, validationSize, alpha);
            if (metrics == null) {
                continue;
            }
            if (best == null || metrics.mae() < best.metrics().orElseThrow().mae()) {
                best = new ForecastEvaluation(MODEL_CROSTON, alpha, 0, Optional.of(metrics));
            }
        }
        if (best == null) {
            return new ForecastEvaluation(MODEL_CROSTON, CROSTON_ALPHA_GRID[0], 0, Optional.empty());
        }
        return best;
    }

    private ForecastMetrics validateHoltCandidate(List<DemandPoint> history,
                                                  int trainingSize,
                                                  int validationSize,
                                                  double alpha,
                                                  double beta) {
        List<Double> trainingValues = history.subList(0, trainingSize).stream()
                .map(point -> (double) point.quantity())
                .toList();
        if (trainingValues.size() < 2) {
            return null;
        }
        HoltState state = runHolt(trainingValues, alpha, beta);
        List<Double> predictions = new ArrayList<>();
        for (int i = 1; i <= validationSize; i++) {
            predictions.add(state.level() + i * state.trend());
        }
        List<Double> actuals = history.subList(trainingSize, trainingSize + validationSize).stream()
                .map(point -> (double) point.quantity())
                .toList();
        return calculateMetrics(predictions, actuals);
    }

    private ForecastMetrics validateCrostonCandidate(List<DemandPoint> history,
                                                     int trainingSize,
                                                     int validationSize,
                                                     double alpha) {
        List<Double> trainingValues = history.subList(0, trainingSize).stream()
                .map(point -> (double) point.quantity())
                .toList();
        CrostonState state = runCroston(trainingValues, alpha);
        if (state == null) {
            return null;
        }
        double forecastValue = Math.max(0, state.demand() / state.interval());
        List<Double> predictions = new ArrayList<>(validationSize);
        for (int i = 0; i < validationSize; i++) {
            predictions.add(forecastValue);
        }
        List<Double> actuals = history.subList(trainingSize, trainingSize + validationSize).stream()
                .map(point -> (double) point.quantity())
                .toList();
        return calculateMetrics(predictions, actuals);
    }

    private List<ForecastPoint> generateHoltForecast(List<DemandPoint> history,
                                                     int horizon,
                                                     double alpha,
                                                     double beta,
                                                     ForecastGranularity granularity) {
        List<Double> values = history.stream()
                .map(point -> (double) point.quantity())
                .toList();
        HoltState state = runHolt(values, alpha, beta);
        List<ForecastPoint> points = new ArrayList<>(horizon);
        LocalDate startDate = history.get(history.size() - 1).date();
        for (int step = 1; step <= horizon; step++) {
            double prediction = Math.max(0, state.level() + step * state.trend());
            points.add(new ForecastPoint(advanceDate(startDate, step, granularity), prediction));
        }
        return points;
    }

    private List<ForecastPoint> generateCrostonForecast(List<DemandPoint> history,
                                                        int horizon,
                                                        double alpha,
                                                        ForecastGranularity granularity) {
        List<Double> values = history.stream()
                .map(point -> (double) point.quantity())
                .toList();
        CrostonState state = runCroston(values, alpha);
        if (state == null) {
            return List.of();
        }
        double forecastValue = Math.max(0, state.demand() / state.interval());
        List<ForecastPoint> points = new ArrayList<>(horizon);
        LocalDate startDate = history.get(history.size() - 1).date();
        for (int step = 1; step <= horizon; step++) {
            points.add(new ForecastPoint(advanceDate(startDate, step, granularity), forecastValue));
        }
        return points;
    }

    private HoltState runHolt(List<Double> values, double alpha, double beta) {
        double level = values.get(0);
        double trend = values.size() > 1 ? values.get(1) - values.get(0) : 0;
        double prevLevel;
        for (int i = 1; i < values.size(); i++) {
            double observation = values.get(i);
            prevLevel = level;
            level = alpha * observation + (1 - alpha) * (level + trend);
            trend = beta * (level - prevLevel) + (1 - beta) * trend;
        }
        return new HoltState(level, trend);
    }

    private CrostonState runCroston(List<Double> values, double alpha) {
        double demand = 0;
        double interval = 0;
        boolean initialized = false;
        int gap = 0;
        for (double observation : values) {
            gap++;
            if (observation <= 0) {
                continue;
            }
            if (!initialized) {
                demand = observation;
                interval = gap;
                initialized = true;
            } else {
                demand = alpha * observation + (1 - alpha) * demand;
                interval = alpha * gap + (1 - alpha) * interval;
            }
            gap = 0;
        }
        if (!initialized) {
            return null;
        }
        return new CrostonState(demand, interval);
    }

    private ForecastMetrics calculateMetrics(List<Double> predictions, List<Double> actuals) {
        DoubleSummaryStatistics maeStats = new DoubleSummaryStatistics();
        double mapeSum = 0;
        int mapeCount = 0;
        for (int i = 0; i < actuals.size(); i++) {
            double actual = actuals.get(i);
            double forecast = predictions.get(i);
            double error = Math.abs(actual - forecast);
            maeStats.accept(error);
            if (actual != 0) {
                mapeSum += error / Math.abs(actual);
                mapeCount++;
            }
        }
        double mae = maeStats.getCount() == 0 ? 0 : maeStats.getAverage();
        double mape = mapeCount == 0 ? 0 : (mapeSum / mapeCount) * 100;
        return new ForecastMetrics(mae, mape, (int) maeStats.getCount());
    }

    private DemandSeries aggregateWeeklySeries(DemandSeries dailySeries, int historyWeeks) {
        Map<LocalDate, Long> weeklyTotals = new HashMap<>();
        for (DemandPoint point : dailySeries.points()) {
            LocalDate weekStart = point.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            weeklyTotals.merge(weekStart, point.quantity(), Long::sum);
        }
        LocalDate endWeek = dailySeries.endDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate startWeek = endWeek.minusWeeks(Math.max(historyWeeks - 1, 0));
        List<DemandPoint> weeklyPoints = new ArrayList<>(historyWeeks);
        LocalDate pointer = startWeek;
        for (int i = 0; i < historyWeeks; i++) {
            long quantity = weeklyTotals.getOrDefault(pointer, 0L);
            weeklyPoints.add(new DemandPoint(pointer, quantity));
            pointer = pointer.plusWeeks(1);
        }
        return new DemandSeries(
                dailySeries.productId(),
                startWeek,
                endWeek,
                List.copyOf(weeklyPoints),
                dailySeries.insufficientData()
        );
    }

    private LocalDate advanceDate(LocalDate startDate, int step, ForecastGranularity granularity) {
        if (granularity == ForecastGranularity.WEEK) {
            return startDate.plusWeeks(step);
        }
        return startDate.plusDays(step);
    }

    private IntermittencyStats analyzeIntermittency(List<DemandPoint> history) {
        if (history == null || history.isEmpty()) {
            return new IntermittencyStats(1.0, 0, 0, true);
        }
        int zeroCount = 0;
        int nonZeroCount = 0;
        int gap = 0;
        List<Integer> intervals = new ArrayList<>();
        for (DemandPoint point : history) {
            gap++;
            if (point.quantity() <= 0) {
                zeroCount++;
                continue;
            }
            nonZeroCount++;
            if (nonZeroCount > 1) {
                intervals.add(gap);
            }
            gap = 0;
        }
        double zeroRatio = (double) zeroCount / history.size();
        double averageInterval = intervals.isEmpty()
                ? history.size()
                : intervals.stream().mapToInt(Integer::intValue).average().orElse(0);
        boolean intermittent = zeroRatio >= INTERMITTENT_ZERO_RATIO
                || averageInterval >= INTERMITTENT_INTERVAL_THRESHOLD;
        return new IntermittencyStats(zeroRatio, averageInterval, nonZeroCount, intermittent);
    }

    private record HoltState(double level, double trend) {
    }

    private record CrostonState(double demand, double interval) {
    }

    private record ForecastEvaluation(String model, double alpha, double beta, Optional<ForecastMetrics> metrics) {
    }

    private record IntermittencyStats(double zeroRatio,
                                      double averageInterval,
                                      int nonZeroCount,
                                      boolean isIntermittent) {
    }
}
