package com.example.kpo.service;

import com.example.kpo.entity.Warehouse;
import com.example.kpo.repository.WarehouseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public WarehouseService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    public List<Warehouse> getAllWarehouses() {
        return warehouseRepository.findAll();
    }
    public Optional<Warehouse>  getWarehouseById(long id) {
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

}
