package com.example.kpo.controller;

import com.example.kpo.dto.forecast.ForecastResult;
import com.example.kpo.dto.forecast.ForecastGranularity;
import com.example.kpo.service.ForecastService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/forecasts")
public class ForecastController {

    private final ForecastService forecastService;

    public ForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @GetMapping
    public ResponseEntity<ForecastResult> getForecast(@RequestParam("productId") Long productId,
                                                      @RequestParam(value = "historyDays", defaultValue = "120") int historyDays,
                                                      @RequestParam(value = "validationWindow", defaultValue = "14") int validationWindow,
                                                      @RequestParam(value = "horizonDays", defaultValue = "14") int horizonDays,
                                                      @RequestParam(value = "granularity", defaultValue = "DAY") String granularity) {
        ForecastGranularity mode = ForecastGranularity.fromString(granularity);
        ForecastResult result = forecastService.forecastProduct(productId, historyDays, validationWindow, horizonDays, mode);
        return ResponseEntity.ok(result);
    }
}
