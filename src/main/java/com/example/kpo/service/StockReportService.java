package com.example.kpo.service;

import com.example.kpo.dto.StockReportRequest;
import com.example.kpo.entity.Category;
import com.example.kpo.entity.Movement;
import com.example.kpo.entity.MovementProduct;
import com.example.kpo.entity.MovementType;
import com.example.kpo.entity.Product;
import com.example.kpo.entity.Warehouse;
import com.example.kpo.repository.CategoryRepository;
import com.example.kpo.repository.MovementRepository;
import com.example.kpo.repository.WarehouseRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.BaseFont;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StockReportService {

    private final MovementRepository movementRepository;
    private final WarehouseRepository warehouseRepository;
    private final CategoryRepository categoryRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final Font titleFont;
    private final Font subtitleFont;
    private final Font tableHeaderFont;
    private final Font tableBodyFont;
    private final Font sectionTitleFont;

    public StockReportService(MovementRepository movementRepository,
                              WarehouseRepository warehouseRepository,
                              CategoryRepository categoryRepository) {
        this.movementRepository = movementRepository;
        this.warehouseRepository = warehouseRepository;
        this.categoryRepository = categoryRepository;

        BaseFont reportBaseFont = loadBaseFont();
        this.titleFont = new Font(reportBaseFont, 16f, Font.BOLD);
        this.subtitleFont = new Font(reportBaseFont, 11f, Font.NORMAL);
        this.tableHeaderFont = new Font(reportBaseFont, 10f, Font.BOLD);
        this.tableBodyFont = new Font(reportBaseFont, 10f, Font.NORMAL);
        this.sectionTitleFont = new Font(reportBaseFont, 12f, Font.BOLD);
    }

    public byte[] generateStockReport(StockReportRequest request) {
        List<StockRow> stockRows = loadStockData(request);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Document document = new Document(PageSize.A4, 36, 36, 48, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            document.add(new Paragraph("Отчёт по остаткам на складах", titleFont));
            document.add(new Paragraph(buildFiltersSummary(request), subtitleFont));
            document.add(new Paragraph(" "));

            PdfPTable detailsTable = buildDetailsTable();
            populateDetailTable(detailsTable, stockRows);
            document.add(detailsTable);

            document.add(new Paragraph(" "));
            Paragraph summaryHeading = new Paragraph("Сводная таблица по категориям", sectionTitleFont);
            summaryHeading.setSpacingBefore(4f);
            summaryHeading.setSpacingAfter(6f);
            document.add(summaryHeading);
            PdfPTable summaryTable = buildSummaryTable();
            populateSummaryTable(summaryTable, stockRows);
            document.add(summaryTable);

            document.close();
        } catch (DocumentException documentException) {
            throw new IllegalStateException("Не удалось сформировать PDF отчёт", documentException);
        }
        return baos.toByteArray();
    }

    private String buildFiltersSummary(StockReportRequest request) {
        List<String> parts = new ArrayList<>();
        if (request.getReportDate() != null) {
            parts.add(String.format("Дата отчёта: %s", DATE_FORMAT.format(request.getReportDate())));
        } else {
            parts.add("Дата отчёта: не указана");
        }

        if (!CollectionUtils.isEmpty(request.getWarehouseIds())) {
            List<String> names = warehouseRepository.findAllById(request.getWarehouseIds()).stream()
                    .map(Warehouse::getName)
                    .sorted(String::compareToIgnoreCase)
                    .collect(Collectors.toList());
            if (!names.isEmpty()) {
                parts.add("Склады: " + String.join(", ", names));
            }
        } else {
            parts.add("Склады: все");
        }

        if (!CollectionUtils.isEmpty(request.getCategoryIds())) {
            List<String> categories = categoryRepository.findAllById(request.getCategoryIds()).stream()
                    .map(Category::getName)
                    .sorted(String::compareToIgnoreCase)
                    .collect(Collectors.toList());
            if (!categories.isEmpty()) {
                parts.add("Категории: " + String.join(", ", categories));
            }
        } else {
            parts.add("Категории: все");
        }

        return String.join(" • ", parts);
    }

    private List<StockRow> loadStockData(StockReportRequest request) {
        LocalDate reportDate = request.getReportDate();
        LocalDateTime reportMoment = reportDate != null
                ? reportDate.atTime(LocalTime.MAX)
                : null;
        Set<Long> warehouseFilter = new HashSet<>(normalizeIds(request.getWarehouseIds()));
        Set<Long> categoryFilter = new HashSet<>(normalizeIds(request.getCategoryIds()));
        if (!warehouseFilter.isEmpty()) {
            Set<Long> existingWarehouseIds = warehouseRepository.findAllById(warehouseFilter).stream()
                    .map(Warehouse::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            warehouseFilter.retainAll(existingWarehouseIds);
        }
        if (!categoryFilter.isEmpty()) {
            Set<Long> existingCategoryIds = categoryRepository.findAllById(categoryFilter).stream()
                    .map(Category::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            categoryFilter.retainAll(existingCategoryIds);
        }

        List<Movement> movements = new ArrayList<>(movementRepository.findAllForReport(reportMoment));
        movements.sort(Comparator.comparing(Movement::getDate));

        Map<StockKey, Integer> totals = new LinkedHashMap<>();
        for (Movement movement : movements) {
            if (movement.getItems() == null) {
                continue;
            }
            for (MovementProduct item : movement.getItems()) {
                Product product = item.getProduct();
                Warehouse sourceWarehouse = movement.getWarehouse();
                Warehouse targetWarehouse = movement.getTargetWarehouse();
                int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
                if (product == null || quantity == 0) {
                    continue;
                }
                MovementType type = movement.getType();
                if (type == null) {
                    continue;
                }
                if (type == MovementType.INBOUND) {
                    adjustTotal(totals, sourceWarehouse, product, quantity);
                } else if (type == MovementType.OUTBOUND) {
                    adjustTotal(totals, sourceWarehouse, product, -quantity);
                } else if (type == MovementType.TRANSFER) {
                    adjustTotal(totals, sourceWarehouse, product, -quantity);
                    adjustTotal(totals, targetWarehouse, product, quantity);
                }
            }
        }

        List<StockRow> stockRows = totals.entrySet().stream()
                .map(entry -> new StockRow(entry.getKey().warehouse, entry.getKey().product, entry.getValue()))
                .filter(row -> row.quantity() > 0)
                .filter(row -> filterRow(row, warehouseFilter, categoryFilter))
                .sorted(Comparator
                        .comparing(StockRow::warehouseName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(StockRow::categoryName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(StockRow::productName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return stockRows;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private PdfPTable buildDetailsTable() {
        PdfPTable table = new PdfPTable(new float[]{4.0f, 3.6f, 1.2f});
        table.setWidthPercentage(100);

        addHeaderCell(table, "Склад / Категория");
        addHeaderCell(table, "Товар");
        addHeaderCell(table, "Количество");
        return table;
    }

    private void populateDetailTable(PdfPTable table, List<StockRow> stockRows) {
        if (stockRows.isEmpty()) {
            PdfPCell emptyCell = new PdfPCell(new Phrase("Нет данных для выбранных фильтров", tableBodyFont));
            emptyCell.setColspan(3);
            emptyCell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            emptyCell.setPadding(12f);
            table.addCell(emptyCell);
            return;
        }
        String currentWarehouse = null;
        String currentCategory = null;
        int warehouseTotal = 0;
        for (StockRow stock : stockRows) {
            Warehouse warehouse = stock.warehouse();
            Product product = stock.product();
            String warehouseName = stock.warehouseName();
            if (!Objects.equals(currentWarehouse, warehouseName)) {
                if (currentWarehouse != null) {
                    addWarehouseTotalRow(table, currentWarehouse, warehouseTotal);
                }
                currentWarehouse = warehouseName;
                warehouseTotal = 0;
                addWarehouseGroupRow(table, warehouseName);
                currentCategory = null;
            }

            String categoryName = stock.categoryName();
            if (!Objects.equals(currentCategory, categoryName)) {
                currentCategory = categoryName;
                addCategoryGroupRow(table, categoryName);
            }

            String productName = stock.productName();
            int quantity = stock.quantity();
            warehouseTotal += quantity;
            addProductRow(table, productName, quantity);
        }
        if (currentWarehouse != null) {
            addWarehouseTotalRow(table, currentWarehouse, warehouseTotal);
        }
    }


    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, tableHeaderFont));
        cell.setGrayFill(0.9f);
        cell.setPadding(6f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private PdfPCell createBodyCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(defaultString(text), tableBodyFont));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setPadding(5f);
        return cell;
    }

    private void addWarehouseGroupRow(PdfPTable table, String warehouseName) {
        PdfPCell cell = new PdfPCell(new Phrase("Склад: " + defaultString(warehouseName), tableHeaderFont));
        cell.setColspan(3);
        cell.setGrayFill(0.95f);
        cell.setPadding(6f);
        table.addCell(cell);
    }

    private void addCategoryGroupRow(PdfPTable table, String categoryName) {
        PdfPCell cell = new PdfPCell(new Phrase("Категория: " + defaultString(categoryName), tableBodyFont));
        cell.setColspan(3);
        cell.setGrayFill(0.97f);
        cell.setPadding(5f);
        table.addCell(cell);
    }

    private void addProductRow(PdfPTable table, String productName, int quantity) {
        table.addCell(createBodyCell(""));
        table.addCell(createBodyCell(productName));
        table.addCell(createNumericCell(quantity));
    }

    private void addWarehouseTotalRow(PdfPTable table, String warehouseName, int totalQuantity) {
        PdfPCell label = new PdfPCell(new Phrase(
                "Итого по складу: " + defaultString(warehouseName),
                tableBodyFont));
        label.setColspan(2);
        label.setHorizontalAlignment(Element.ALIGN_RIGHT);
        label.setGrayFill(0.92f);
        label.setPadding(6f);
        table.addCell(label);

        PdfPCell value = createNumericCell(totalQuantity);
        value.setGrayFill(0.92f);
        table.addCell(value);
    }

    private PdfPCell createNumericCell(int value) {
        PdfPCell cell = new PdfPCell(new Phrase(String.valueOf(value), tableBodyFont));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setPadding(5f);
        return cell;
    }

    private void adjustTotal(Map<StockKey, Integer> totals,
                             Warehouse warehouse,
                             Product product,
                             int delta) {
        if (warehouse == null || product == null || delta == 0) {
            return;
        }
        StockKey key = new StockKey(warehouse, product);
        totals.merge(key, delta, Integer::sum);
    }

    private boolean filterRow(StockRow row, Set<Long> warehouseFilter, Set<Long> categoryFilter) {
        boolean warehouseMatch = warehouseFilter.isEmpty()
                || (row.warehouse() != null
                && row.warehouse().getId() != null
                && warehouseFilter.contains(row.warehouse().getId()));
        boolean categoryMatch = categoryFilter.isEmpty()
                || (row.product() != null
                && row.product().getCategory() != null
                && row.product().getCategory().getId() != null
                && categoryFilter.contains(row.product().getCategory().getId()));
        return warehouseMatch && categoryMatch;
    }

    private PdfPTable buildSummaryTable() {
        PdfPTable table = new PdfPTable(new float[]{3.2f, 3.2f, 1.3f});
        table.setWidthPercentage(100);

        addHeaderCell(table, "Склад");
        addHeaderCell(table, "Категория");
        addHeaderCell(table, "Количество");
        return table;
    }

    private void populateSummaryTable(PdfPTable table, List<StockRow> stockRows) {
        Map<String, Map<String, Integer>> summary = summarizeByWarehouseAndCategory(stockRows);
        if (summary.isEmpty()) {
            PdfPCell emptyCell = new PdfPCell(new Phrase("Нет данных для сводной таблицы", tableBodyFont));
            emptyCell.setColspan(3);
            emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            emptyCell.setPadding(12f);
            table.addCell(emptyCell);
            return;
        }
        for (Map.Entry<String, Map<String, Integer>> warehouseEntry : summary.entrySet()) {
            addSummaryWarehouseRow(table, warehouseEntry.getKey());
            for (Map.Entry<String, Integer> categoryEntry : warehouseEntry.getValue().entrySet()) {
                table.addCell(createBodyCell(""));
                table.addCell(createBodyCell(categoryEntry.getKey()));
                table.addCell(createNumericCell(categoryEntry.getValue()));
            }
        }
    }

    private void addSummaryWarehouseRow(PdfPTable table, String warehouseName) {
        PdfPCell cell = new PdfPCell(new Phrase("Склад: " + defaultString(warehouseName), tableHeaderFont));
        cell.setColspan(3);
        cell.setGrayFill(0.95f);
        cell.setPadding(6f);
        table.addCell(cell);
    }

    private Map<String, Map<String, Integer>> summarizeByWarehouseAndCategory(List<StockRow> stockRows) {
        Map<String, Map<String, Integer>> summary = new LinkedHashMap<>();
        for (StockRow stock : stockRows) {
            String warehouseName = stock.warehouseName();
            String categoryName = stock.categoryName();
            int quantity = stock.quantity();

            Map<String, Integer> categoryTotals = summary.computeIfAbsent(warehouseName, key -> new LinkedHashMap<>());
            categoryTotals.merge(categoryName, quantity, Integer::sum);
        }
        return summary;
    }

    private BaseFont loadBaseFont() {
        try (InputStream fontStream = StockReportService.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            if (fontStream == null) {
                throw new IllegalStateException("Не найден файл шрифта для отчёта");
            }
            byte[] fontBytes = fontStream.readAllBytes();
            return BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontBytes, null);
        } catch (IOException | DocumentException exception) {
            throw new IllegalStateException("Не удалось загрузить шрифт для PDF отчёта", exception);
        }
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static final class StockKey {
        private final Warehouse warehouse;
        private final Product product;
        private final Long warehouseId;
        private final Long productId;

        private StockKey(Warehouse warehouse, Product product) {
            this.warehouse = warehouse;
            this.product = product;
            this.warehouseId = warehouse != null ? warehouse.getId() : null;
            this.productId = product != null ? product.getId() : null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            StockKey stockKey = (StockKey) o;
            return Objects.equals(warehouseId, stockKey.warehouseId)
                    && Objects.equals(productId, stockKey.productId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(warehouseId, productId);
        }
    }

    private record StockRow(Warehouse warehouse, Product product, int quantity) {
        String warehouseName() {
            return warehouse != null ? nonEmptyOrDash(warehouse.getName()) : "—";
        }

        String categoryName() {
            if (product != null && product.getCategory() != null) {
                return nonEmptyOrDash(product.getCategory().getName());
            }
            return "—";
        }

        String productName() {
            return product != null ? nonEmptyOrDash(product.getName()) : "—";
        }

        private String nonEmptyOrDash(String value) {
            String normalized = defaultString(value);
            return normalized.isBlank() ? "—" : normalized;
        }
    }
}
