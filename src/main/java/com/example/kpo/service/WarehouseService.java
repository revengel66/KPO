package com.example.kpo.service;

import com.example.kpo.dto.WarehouseProductResponse;
import com.example.kpo.entity.Product;
import com.example.kpo.entity.Warehouse;
import com.example.kpo.entity.WarehouseProduct;
import com.example.kpo.repository.WarehouseProductRepository;
import com.example.kpo.repository.WarehouseRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseProductRepository warehouseProductRepository;

    public WarehouseService(WarehouseRepository warehouseRepository,
                            WarehouseProductRepository warehouseProductRepository) {
        this.warehouseRepository = warehouseRepository;
        this.warehouseProductRepository = warehouseProductRepository;
    }

    public List<Warehouse> getAllWarehouses() {
        return warehouseRepository.findAll();
    }

    public Optional<Warehouse> getWarehouseById(long id) {
        return warehouseRepository.findById(id);
    }

    public Warehouse saveWarehouse(Warehouse warehouse) {
        return warehouseRepository.save(warehouse);
    }

    public void deleteWarehouseById(long id) {
        warehouseRepository.deleteById(id);
    }

    public Warehouse updateWarehouse(Warehouse warehouse) {
        return warehouseRepository.save(warehouse);
    }

    public List<WarehouseProductResponse> getWarehouseProducts(Warehouse warehouse) {
        List<WarehouseProduct> stocks = warehouseProductRepository.findByWarehouse(warehouse);
        List<WarehouseProduct> activeStocks = new ArrayList<>();
        for (WarehouseProduct stock : stocks) {
            Integer quantity = stock.getQuantity();
            if (quantity == null || quantity <= 0) {
                warehouseProductRepository.delete(stock);
                continue;
            }
            activeStocks.add(stock);
        }
        return activeStocks.stream()
                .sorted(Comparator.comparingLong(stock -> {
                    Product product = stock.getProduct();
                    return product != null && product.getId() != null ? product.getId() : Long.MAX_VALUE;
                }))
                .map(this::mapToResponse)
                .toList();
    }

    private WarehouseProductResponse mapToResponse(WarehouseProduct stock) {
        Product product = stock.getProduct();
        Long productId = product != null ? product.getId() : null;
        String productName = product != null ? product.getName() : null;
        Integer quantity = stock.getQuantity() != null ? stock.getQuantity() : 0;
        return new WarehouseProductResponse(productId, productName, quantity);
    }
}
