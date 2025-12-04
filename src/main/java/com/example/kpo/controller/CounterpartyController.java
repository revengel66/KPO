package com.example.kpo.controller;

import com.example.kpo.entity.Counterparty;
import com.example.kpo.service.CounterpartyService;
import jakarta.validation.Valid;
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
@RequestMapping("/counterparties")
public class CounterpartyController {

    private final CounterpartyService counterpartyService;

    public CounterpartyController(CounterpartyService counterpartyService) {
        this.counterpartyService = counterpartyService;
    }

    @GetMapping
    public ResponseEntity<List<Counterparty>> getAllCounterparties() {
        return ResponseEntity.ok(counterpartyService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Counterparty> getCounterpartyById(@PathVariable Long id) {
        return counterpartyService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Counterparty> createCounterparty(@Valid @RequestBody Counterparty counterparty) {
        Counterparty created = counterpartyService.create(counterparty);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Counterparty> updateCounterparty(@PathVariable Long id,
                                                           @Valid @RequestBody Counterparty counterparty) {
        return counterpartyService.update(id, counterparty)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCounterparty(@PathVariable Long id) {
        if (counterpartyService.getById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        counterpartyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
