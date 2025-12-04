package com.example.kpo;

import com.example.kpo.dto.LoginRequest;
import com.example.kpo.entity.Admin;
import com.example.kpo.entity.Category;
import com.example.kpo.repository.AdminRepository;
import com.example.kpo.repository.CategoryRepository;
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
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CategoryControllerIntegrationTest {

    private static final String USERNAME = "CATEGORY_TEST";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        categoryRepository.deleteAll();
        adminRepository.deleteAll();

        Admin admin = new Admin();
        admin.setUsername(USERNAME);
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        adminRepository.save(admin);
    }

    @Test
    @DisplayName("GET /categories возвращает все категории")
    void getAllCategoriesReturnsData() throws Exception {
        Category first = categoryRepository.save(new Category(null, "Первая"));
        Category second = categoryRepository.save(new Category(null, "Вторая"));

        mockMvc.perform(get("/categories")
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        first.getId().intValue(),
                        second.getId().intValue()
                )))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Первая", "Вторая")));
    }

    @Test
    @DisplayName("GET /categories/{id} возвращает категорию по идентификатору")
    void getCategoryByIdReturnsEntity() throws Exception {
        Category category = categoryRepository.save(new Category(null, "Одежда"));

        mockMvc.perform(get("/categories/{id}", category.getId())
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(category.getId().intValue())))
                .andExpect(jsonPath("$.name", is("Одежда")));
    }

    @Test
    @DisplayName("POST /categories создаёт категорию")
    void createCategoryReturnsCreated() throws Exception {
        Category payload = new Category(null, "Техника");

        mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Техника")));

        assertThat(categoryRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("PUT /categories/{id} обновляет категорию")
    void updateCategoryReturnsUpdated() throws Exception {
        Category existing = categoryRepository.save(new Category(null, "Старая"));
        Category payload = new Category(null, "Новая");

        mockMvc.perform(put("/categories/{id}", existing.getId())
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Новая")));

        Category refreshed = categoryRepository.findById(existing.getId()).orElseThrow();
        assertThat(refreshed.getName()).isEqualTo("Новая");
    }

    @Test
    @DisplayName("DELETE /categories/{id} удаляет категорию")
    void deleteCategoryReturnsNoContent() throws Exception {
        Category category = categoryRepository.save(new Category(null, "Удаляемая"));

        mockMvc.perform(delete("/categories/{id}", category.getId())
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isNoContent());

        assertThat(categoryRepository.findById(category.getId())).isEmpty();
    }

    @Test
    @DisplayName("POST /categories отклоняет пустое имя категории")
    void createCategoryValidationError() throws Exception {
        Category payload = new Category(null, "");

        mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name", is("Category name is required")));

        assertThat(categoryRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("PUT /categories/{id} отклоняет пустое имя категории")
    void updateCategoryValidationError() throws Exception {
        Category existing = categoryRepository.save(new Category(null, "Существующая"));
        Category payload = new Category(null, "");

        mockMvc.perform(put("/categories/{id}", existing.getId())
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name", is("Category name is required")));

        Category refreshed = categoryRepository.findById(existing.getId()).orElseThrow();
        assertThat(refreshed.getName()).isEqualTo("Существующая");
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
