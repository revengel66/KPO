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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReportServiceTest {

    @Mock
    private MovementRepository movementRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private StockReportService stockReportService;

    @BeforeEach
    void setUp() {
        stockReportService = new StockReportService(movementRepository, warehouseRepository, categoryRepository);
    }

    @Test
    @DisplayName("Загруженные данные агрегируются по складам и категориям")
    void loadStockDataAggregatesMovements() throws Exception {
        Warehouse warehouseA = warehouse(1L, "Склад A");
        Warehouse warehouseB = warehouse(2L, "Склад B");
        Category electronics = category(1L, "Электроника");
        Category furniture = category(2L, "Мебель");
        Product scanner = product(11L, "Сканер", electronics);
        Product desk = product(12L, "Стол", furniture);

        List<Movement> movements = List.of(
                movement(LocalDateTime.of(2025, 1, 1, 9, 0), MovementType.INBOUND, warehouseA, null,
                        movementItem(scanner, 10)),
                movement(LocalDateTime.of(2025, 1, 2, 11, 0), MovementType.OUTBOUND, warehouseA, null,
                        movementItem(scanner, 3)),
                movement(LocalDateTime.of(2025, 1, 3, 8, 0), MovementType.INBOUND, warehouseA, null,
                        movementItem(desk, 5)),
                movement(LocalDateTime.of(2025, 1, 4, 15, 0), MovementType.TRANSFER, warehouseA, warehouseB,
                        movementItem(desk, 2))
        );
        when(movementRepository.findAllForReport(any())).thenReturn(movements);

        StockReportRequest request = new StockReportRequest();
        request.setReportDate(LocalDate.of(2025, 1, 5));

        List<Object> rows = new ArrayList<>(invokeLoadStockData(request));
        assertThat(rows).hasSize(3);
        rows.sort(Comparator.comparing(this::warehouseName)
                .thenComparing(this::categoryName)
                .thenComparing(this::productName));

        assertRow(rows.get(0), "Склад A", "Электроника", "Сканер", 7);
        assertRow(rows.get(1), "Склад A", "Мебель", "Стол", 3);
        assertRow(rows.get(2), "Склад B", "Мебель", "Стол", 2);

        byte[] pdf = stockReportService.generateStockReport(request);
        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("Данные отчёта фильтруются по выбранным складам и категориям")
    void loadStockDataRespectsWarehouseAndCategoryFilters() throws Exception {
        Warehouse warehouseA = warehouse(5L, "Основной склад");
        Warehouse warehouseB = warehouse(6L, "Удалённый склад");
        Category electronics = category(3L, "Электроника");
        Category furniture = category(4L, "Мебель");
        Product monitor = product(21L, "Монитор", electronics);
        Product chair = product(22L, "Стул", furniture);

        List<Movement> movements = List.of(
                movement(LocalDateTime.of(2025, 2, 1, 10, 0), MovementType.INBOUND, warehouseA, null,
                        movementItem(monitor, 5)),
                movement(LocalDateTime.of(2025, 2, 3, 12, 0), MovementType.INBOUND, warehouseB, null,
                        movementItem(chair, 7))
        );
        when(movementRepository.findAllForReport(any())).thenReturn(movements);
        when(warehouseRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            List<Warehouse> result = new ArrayList<>();
            for (Long id : ids) {
                if (warehouseB.getId().equals(id)) {
                    result.add(warehouseB);
                }
            }
            return result;
        });
        when(categoryRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            List<Category> result = new ArrayList<>();
            for (Long id : ids) {
                if (furniture.getId().equals(id)) {
                    result.add(furniture);
                }
            }
            return result;
        });

        StockReportRequest request = new StockReportRequest();
        request.setReportDate(LocalDate.of(2025, 2, 4));
        request.setWarehouseIds(List.of(warehouseB.getId()));
        request.setCategoryIds(List.of(furniture.getId()));

        List<Object> rows = invokeLoadStockData(request);
        assertThat(rows).hasSize(1);
        assertRow(rows.get(0), warehouseB.getName(), furniture.getName(), chair.getName(), 7);
    }

    @Test
    @DisplayName("Загрузка данных возвращает пустой список при отсутствии движений")
    void loadStockDataReturnsEmptyListWhenNoMovements() throws Exception {
        when(movementRepository.findAllForReport(any())).thenReturn(List.of());

        StockReportRequest request = new StockReportRequest();

        List<Object> rows = invokeLoadStockData(request);
        assertThat(rows).isEmpty();

        byte[] pdf = stockReportService.generateStockReport(request);
        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("Дата отчёта конвертируется в конец дня при запросе движений")
    void generateStockReportUsesEndOfDayMoment() {
        when(movementRepository.findAllForReport(any())).thenReturn(List.of());

        StockReportRequest request = new StockReportRequest();
        request.setReportDate(LocalDate.of(2025, 3, 15));

        stockReportService.generateStockReport(request);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(movementRepository).findAllForReport(captor.capture());
        assertThat(captor.getValue()).isEqualTo(request.getReportDate().atTime(LocalTime.MAX));
    }

    @Test
    @DisplayName("При отсутствии даты отчёта репозиторий вызывается без ограничения по времени")
    void generateStockReportWithoutDatePassesNullToRepository() {
        when(movementRepository.findAllForReport(any())).thenReturn(List.of());

        StockReportRequest request = new StockReportRequest();

        stockReportService.generateStockReport(request);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(movementRepository).findAllForReport(captor.capture());
        assertThat(captor.getValue()).isNull();
    }

    private Warehouse warehouse(Long id, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setName(name);
        warehouse.setInfo("");
        return warehouse;
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private Product product(Long id, String name, Category category) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setCategory(category);
        return product;
    }

    private Movement movement(LocalDateTime date,
                              MovementType type,
                              Warehouse warehouse,
                              Warehouse targetWarehouse,
                              MovementProduct... items) {
        Movement movement = new Movement();
        movement.setDate(date);
        movement.setType(type);
        movement.setWarehouse(warehouse);
        movement.setTargetWarehouse(targetWarehouse);
        movement.setItems(new ArrayList<>());
        for (MovementProduct item : items) {
            item.setMovement(movement);
            movement.getItems().add(item);
        }
        return movement;
    }

    private MovementProduct movementItem(Product product, int quantity) {
        MovementProduct item = new MovementProduct();
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    @SuppressWarnings("unchecked")
    private List<Object> invokeLoadStockData(StockReportRequest request) {
        try {
            var method = StockReportService.class.getDeclaredMethod("loadStockData", StockReportRequest.class);
            method.setAccessible(true);
            return (List<Object>) method.invoke(stockReportService, request);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось загрузить данные отчёта через reflection", exception);
        }
    }

    private void assertRow(Object row,
                           String expectedWarehouse,
                           String expectedCategory,
                           String expectedProduct,
                           int expectedQuantity) {
        assertThat(warehouseName(row)).isEqualTo(expectedWarehouse);
        assertThat(categoryName(row)).isEqualTo(expectedCategory);
        assertThat(productName(row)).isEqualTo(expectedProduct);
        assertThat(quantity(row)).isEqualTo(expectedQuantity);
    }

    private String warehouseName(Object row) {
        return invokeString(row, "warehouseName");
    }

    private String categoryName(Object row) {
        return invokeString(row, "categoryName");
    }

    private String productName(Object row) {
        return invokeString(row, "productName");
    }

    private int quantity(Object row) {
        return invokeInt(row, "quantity");
    }

    private String invokeString(Object row, String methodName) {
        try {
            var method = row.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return (String) method.invoke(row);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось вызвать метод " + methodName + " на строке отчёта", exception);
        }
    }

    private int invokeInt(Object row, String methodName) {
        try {
            var method = row.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return (int) method.invoke(row);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось вызвать метод " + methodName + " на строке отчёта", exception);
        }
    }
}
