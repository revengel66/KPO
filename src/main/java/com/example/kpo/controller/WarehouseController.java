package com.example.kpo.controller;

import com.example.kpo.entity.Warehouse;
import com.example.kpo.service.WarehouseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/warehouses")
public class WarehouseController {
    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping
    public ResponseEntity<List<Warehouse>> getAllWarehouses() {
        return ResponseEntity.ok(warehouseService.getAllWarehouses());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Warehouse> getWarehouseById(@PathVariable Long id) {
        return warehouseService.getWarehouseById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Warehouse> addWarehouse(@RequestBody Warehouse warehouse) {
        Warehouse savedWarehouse = warehouseService.saveWarehouse(warehouse);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedWarehouse);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Warehouse> updateWarehouse(@PathVariable Long id,
                                                     @RequestBody Warehouse warehouse) {
        return warehouseService.getWarehouseById(id)
                .map(existingWarehouse -> {
                    existingWarehouse.setName(warehouse.getName());
                    existingWarehouse.setInfo(warehouse.getInfo());
                    Warehouse updatedWarehouse = warehouseService.updateWarehouse(existingWarehouse);
                    return ResponseEntity.ok(updatedWarehouse);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWarehouse(@PathVariable Long id) {
        if (warehouseService.getWarehouseById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        warehouseService.deleteWarehouseById(id);
        return ResponseEntity.noContent().build();
    }
}
