package com.example.kpo.service;

import com.example.kpo.entity.Category;
import com.example.kpo.entity.Product;
import com.example.kpo.repository.CategoryRepository;
import com.example.kpo.repository.MovementProductRepository;
import com.example.kpo.repository.ProductRepository;
import com.example.kpo.repository.WarehouseProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MovementProductRepository movementProductRepository;
    private final WarehouseProductRepository warehouseProductRepository;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          MovementProductRepository movementProductRepository,
                          WarehouseProductRepository warehouseProductRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.movementProductRepository = movementProductRepository;
        this.warehouseProductRepository = warehouseProductRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public Product createProduct(Product product) {
        product.setCategory(resolveCategory(product.getCategory()));
        return productRepository.save(product);
    }

    public Optional<Product> updateProduct(Long id, Product product) {
        return productRepository.findById(id)
                .map(existing -> {
                    existing.setName(product.getName());
                    existing.setInfo(product.getInfo());
                    existing.setCategory(resolveCategory(product.getCategory()));
                    return productRepository.save(existing);
                });
    }

    public void deleteProduct(Long id) {
        if (movementProductRepository.existsByProductId(id)
                || warehouseProductRepository.existsByProductId(id)) {
            throw new IllegalArgumentException("Product is used in movements or stock and cannot be deleted");
        }
        productRepository.deleteById(id);
    }

    private Category resolveCategory(Category category) {
        if (category == null || category.getId() == null) {
            throw new IllegalArgumentException("Category id is required");
        }
        return categoryRepository.findById(category.getId())
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));
    }
}
