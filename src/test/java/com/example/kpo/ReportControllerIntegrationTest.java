package com.example.kpo;

import com.example.kpo.dto.LoginRequest;
import com.example.kpo.dto.StockReportRequest;
import com.example.kpo.entity.Admin;
import com.example.kpo.entity.Category;
import com.example.kpo.entity.Counterparty;
import com.example.kpo.entity.Employee;
import com.example.kpo.entity.Movement;
import com.example.kpo.entity.MovementProduct;
import com.example.kpo.entity.MovementType;
import com.example.kpo.entity.Product;
import com.example.kpo.entity.Warehouse;
import com.example.kpo.repository.AdminRepository;
import com.example.kpo.repository.CategoryRepository;
import com.example.kpo.repository.CounterpartyRepository;
import com.example.kpo.repository.EmployeeRepository;
import com.example.kpo.repository.MovementRepository;
import com.example.kpo.repository.ProductRepository;
import com.example.kpo.repository.WarehouseProductRepository;
import com.example.kpo.repository.WarehouseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerIntegrationTest {

    private static final String USERNAME = "REPORT_TEST";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MovementRepository movementRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private WarehouseProductRepository warehouseProductRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private Warehouse centralWarehouse;
    private Warehouse remoteWarehouse;
    private Category electronicsCategory;
    private Category furnitureCategory;
    private Product tabletProduct;
    private Product chairProduct;
    private Employee employee;
    private Counterparty counterparty;

    @BeforeEach
    void setUp() {
        movementRepository.deleteAll();
        warehouseProductRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        employeeRepository.deleteAll();
        counterpartyRepository.deleteAll();
        adminRepository.deleteAll();

        Admin admin = new Admin();
        admin.setUsername(USERNAME);
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        adminRepository.save(admin);

        electronicsCategory = categoryRepository.save(new Category(null, "Электроника"));
        furnitureCategory = categoryRepository.save(new Category(null, "Мебель"));
        tabletProduct = new Product(null, "Планшет", "10\"");
        tabletProduct.setCategory(electronicsCategory);
        tabletProduct = productRepository.save(tabletProduct);

        chairProduct = new Product(null, "Стул офисный", "эргономичный");
        chairProduct.setCategory(furnitureCategory);
        chairProduct = productRepository.save(chairProduct);

        centralWarehouse = warehouseRepository.save(new Warehouse(null, "Склад «Центральный»", "Основной"));
        remoteWarehouse = warehouseRepository.save(new Warehouse(null, "Склад «Удалённый»", "Регион"));

        employee = employeeRepository.save(new Employee(null, "Иван Петров", "+79000000000", "оператор"));
        counterparty = counterpartyRepository.save(new Counterparty(null, "ООО «Поставщик»", "+79001112233", "договор №1"));

        createInboundMovement(centralWarehouse, tabletProduct, 15, LocalDateTime.of(2025, 1, 5, 10, 0));
        createInboundMovement(remoteWarehouse, chairProduct, 20, LocalDateTime.of(2025, 1, 8, 12, 30));
    }

    @Test
    @DisplayName("POST /reports/stock возвращает PDF со списком остатков")
    void generateStockReportReturnsPdf() throws Exception {
        StockReportRequest request = new StockReportRequest();
        request.setReportDate(LocalDate.of(2025, 1, 10));

        MvcResult result = mockMvc.perform(post("/reports/stock")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("stock-report-")))
                .andReturn();

        String text = extractPdfText(result.getResponse().getContentAsByteArray());
        assertThat(text).contains("Склад: " + centralWarehouse.getName());
        assertThat(text).contains("Склад: " + remoteWarehouse.getName());
        assertThat(text).contains(tabletProduct.getName());
        assertThat(text).contains(chairProduct.getName());
    }

    @Test
    @DisplayName("POST /reports/stock учитывает фильтры по складу и категории")
    void generateStockReportRespectsFilters() throws Exception {
        StockReportRequest request = new StockReportRequest();
        request.setReportDate(LocalDate.of(2025, 1, 10));
        request.setWarehouseIds(List.of(remoteWarehouse.getId()));
        request.setCategoryIds(List.of(furnitureCategory.getId()));

        MvcResult result = mockMvc.perform(post("/reports/stock")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andReturn();

        String text = extractPdfText(result.getResponse().getContentAsByteArray());
        assertThat(text).contains(remoteWarehouse.getName());
        assertThat(text).contains(chairProduct.getName());
        assertThat(text).doesNotContain(centralWarehouse.getName());
        assertThat(text).doesNotContain(tabletProduct.getName());
    }

    @Test
    @DisplayName("POST /reports/stock отображает пустое состояние для дат до первых движений")
    void generateStockReportBeforeMovementsShowsEmptyMessage() throws Exception {
        StockReportRequest request = new StockReportRequest();
        request.setReportDate(LocalDate.of(2024, 12, 31));

        MvcResult result = mockMvc.perform(post("/reports/stock")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andReturn();

        String text = extractPdfText(result.getResponse().getContentAsByteArray());
        assertThat(text).contains("Нет данных для выбранных фильтров");
    }

    private void createInboundMovement(Warehouse warehouse, Product product, int quantity, LocalDateTime dateTime) {
        Movement movement = new Movement();
        movement.setDate(dateTime);
        movement.setType(MovementType.INBOUND);
        movement.setWarehouse(warehouse);
        movement.setEmployee(employee);
        movement.setCounterparty(counterparty);
        movement.setItems(new ArrayList<>());

        MovementProduct item = new MovementProduct();
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setMovement(movement);
        movement.getItems().add(item);

        movementRepository.save(movement);
    }

    private String obtainToken() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(USERNAME);
        loginRequest.setPassword(PASSWORD);

        MvcResult response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(response.getResponse().getContentAsString()).get("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private String extractPdfText(byte[] pdfBytes) throws Exception {
        try (PdfReader reader = new PdfReader(new ByteArrayInputStream(pdfBytes))) {
            com.lowagie.text.pdf.parser.PdfTextExtractor extractor =
                    new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
            StringBuilder builder = new StringBuilder();
            int pages = reader.getNumberOfPages();
            for (int page = 1; page <= pages; page++) {
                builder.append(extractor.getTextFromPage(page));
            }
            return builder.toString();
        }
    }
}
