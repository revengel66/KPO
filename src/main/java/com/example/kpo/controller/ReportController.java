package com.example.kpo.controller;

import com.example.kpo.dto.StockReportRequest;
import com.example.kpo.service.StockReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final StockReportService stockReportService;

    public ReportController(StockReportService stockReportService) {
        this.stockReportService = stockReportService;
    }

    @PostMapping("/stock")
    public ResponseEntity<byte[]> generateStockReport(@RequestBody StockReportRequest request) {
        byte[] pdfBytes = stockReportService.generateStockReport(request);
        String filename = String.format("stock-report-%s.pdf",
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
