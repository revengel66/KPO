package com.example.kpo;

import com.example.kpo.dto.LoginRequest;
import com.example.kpo.entity.Admin;
import com.example.kpo.entity.Category;
import com.example.kpo.entity.Product;
import com.example.kpo.entity.Warehouse;
import com.example.kpo.entity.WarehouseProduct;
import com.example.kpo.repository.AdminRepository;
import com.example.kpo.repository.CategoryRepository;
import com.example.kpo.repository.MovementRepository;
import com.example.kpo.repository.ProductRepository;
import com.example.kpo.repository.WarehouseProductRepository;
import com.example.kpo.repository.WarehouseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerIntegrationTest {

    private static final String USERNAME = "PRODUCT_TEST";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MovementRepository movementRepository;

    @Autowired
    private WarehouseProductRepository warehouseProductRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private Category defaultCategory;

    @BeforeEach
    void setUp() {
        movementRepository.deleteAll();
        warehouseProductRepository.deleteAll();
        warehouseRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        adminRepository.deleteAll();

        Admin admin = new Admin();
        admin.setUsername(USERNAME);
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        adminRepository.save(admin);

        defaultCategory = categoryRepository.save(new Category(null, "Категория"));
    }

    @Test
    @DisplayName("GET /products возвращает все товары")
    void getAllProductsReturnsData() throws Exception {
        Product first = new Product(null, "Товар 1", null);
        first.setCategory(defaultCategory);
        productRepository.save(first);
        Product second = new Product(null, "Товар 2", null);
        second.setCategory(defaultCategory);
        productRepository.save(second);

        mockMvc.perform(get("/products")
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        first.getId().intValue(),
                        second.getId().intValue()
                )));
    }

    @Test
    @DisplayName("GET /products/{id} возвращает товар по идентификатору")
    void getProductByIdReturnsEntity() throws Exception {
        Product product = new Product(null, "Компьютер", "описание");
        product.setCategory(defaultCategory);
        productRepository.save(product);

        mockMvc.perform(get("/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(product.getId().intValue())))
                .andExpect(jsonPath("$.name", is("Компьютер")))
                .andExpect(jsonPath("$.category.id", is(defaultCategory.getId().intValue())));
    }

    @Test
    @DisplayName("POST /products создаёт товар с категориями")
    void createProductReturnsCreated() throws Exception {
        Category shoes = categoryRepository.save(new Category(null, "Обувь"));
        Product payload = new Product(null, "Кроссовки", "зимние");
        payload.setCategory(refCategory(shoes));

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name", is("Кроссовки")))
                .andExpect(jsonPath("$.category.id", is(shoes.getId().intValue())));

        assertThat(productRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("POST /products проверяет валидацию имени")
    void createProductValidationError() throws Exception {
        Product payload = new Product(null, "", null);
        payload.setCategory(refCategory(defaultCategory));

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name", is("Product name is required")));
    }

    @Test
    @DisplayName("POST /products возвращает 404 при отсутствии категории")
    void createProductCategoryNotFound() throws Exception {
        Category ref = new Category();
        ref.setId(999L);
        Product payload = new Product(null, "Стол", null);
        payload.setCategory(ref);

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Category not found")));
    }

    @Test
    @DisplayName("PUT /products/{id} обновляет имя и категории")
    void updateProductReturnsUpdated() throws Exception {
        Product product = new Product(null, "Старое имя", null);
        product.setCategory(defaultCategory);
        productRepository.save(product);
        Category category = categoryRepository.save(new Category(null, "Аксессуары"));
        Product payload = new Product(null, "Новое имя", "описание");
        payload.setCategory(refCategory(category));

        mockMvc.perform(put("/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Новое имя")))
                .andExpect(jsonPath("$.category.id", is(category.getId().intValue())));

        Product refreshed = productRepository.findById(product.getId()).orElseThrow();
        assertThat(refreshed.getName()).isEqualTo("Новое имя");
    }

    @Test
    @DisplayName("PUT /products/{id} возвращает 404 если товара нет")
    void updateProductNotFound() throws Exception {
        Product payload = new Product(null, "Имя", null);
        payload.setCategory(refCategory(defaultCategory));

        mockMvc.perform(put("/products/{id}", 12345)
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /products/{id} удаляет товар")
    void deleteProductReturnsNoContent() throws Exception {
        Product product = new Product(null, "Удаляемый", null);
        product.setCategory(defaultCategory);
        productRepository.save(product);

        mockMvc.perform(delete("/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isNoContent());

        assertThat(productRepository.findById(product.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /products/{id} возвращает 409 если товар используется в остатках")
    void deleteProductReturnsConflictWhenInStock() throws Exception {
        Product product = new Product(null, "В остатках", null);
        product.setCategory(defaultCategory);
        productRepository.save(product);

        Warehouse warehouse = warehouseRepository.save(new Warehouse(null, "Склад тест", "info"));
        warehouseProductRepository.save(new WarehouseProduct(warehouse, product, 5));

        mockMvc.perform(delete("/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Cannot delete product that is referenced by movements or stock")));
    }

    @Test
    @DisplayName("DELETE /products/{id} возвращает 404 если товара нет")
    void deleteProductNotFound() throws Exception {
        mockMvc.perform(delete("/products/{id}", 999)
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isNotFound());
    }

    private Category refCategory(Category category) {
        Category ref = new Category();
        ref.setId(category.getId());
        return ref;
    }

    private String obtainToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(USERNAME);
        request.setPassword(PASSWORD);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
