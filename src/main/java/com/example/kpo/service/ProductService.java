package com.example.kpo.service;

import com.example.kpo.entity.Category;
import com.example.kpo.entity.Product;
import com.example.kpo.repository.CategoryRepository;
import com.example.kpo.repository.MovementProductRepository;
import com.example.kpo.repository.ProductRepository;
import com.example.kpo.repository.WarehouseProductRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
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

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        ensureProductNotUsed(product);
        productRepository.delete(product);
    }

    private Category resolveCategory(Category category) {
        if (category == null || category.getId() == null) {
            throw new IllegalArgumentException("Category id is required");
        }
        return categoryRepository.findById(category.getId())
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));
    }

    private void ensureProductNotUsed(Product product) {
        boolean referencedInMovements = movementProductRepository.existsByProduct(product);
        boolean referencedInStock = warehouseProductRepository.existsByProduct(product);
        if (referencedInMovements || referencedInStock) {
            throw new IllegalStateException("Cannot delete product that is referenced by movements or stock");
        }
    }
}
