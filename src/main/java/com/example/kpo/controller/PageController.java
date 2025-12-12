package com.example.kpo.controller;

import com.example.kpo.entity.MovementType;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {

    @GetMapping(value = {"/", "/login"}, produces = MediaType.TEXT_HTML_VALUE)
    public String loginPage() {
        return "login";
    }

    @GetMapping(value = "/dashboard", produces = MediaType.TEXT_HTML_VALUE)
    public String dashboardStub() {
        return "dashboard";
    }

    @GetMapping(value = "/movements/page", produces = MediaType.TEXT_HTML_VALUE)
    public String movementsHomePage() {
        return "movements-home";
    }

    @GetMapping(value = "/warehouses/page", produces = MediaType.TEXT_HTML_VALUE)
    public String warehousesPage() {
        return "warehouses";
    }

    @GetMapping(value = "/categories/page", produces = MediaType.TEXT_HTML_VALUE)
    public String categoriesPage() {
        return "categories";
    }

    @GetMapping(value = "/products/page", produces = MediaType.TEXT_HTML_VALUE)
    public String productsPage() {
        return "products";
    }

    @GetMapping(value = "/counterparties/page", produces = MediaType.TEXT_HTML_VALUE)
    public String counterpartiesPage() {
        return "counterparties";
    }

    @GetMapping(value = "/employees/page", produces = MediaType.TEXT_HTML_VALUE)
    public String employeesPage() {
        return "employees";
    }

    @GetMapping(value = "/deliveries", produces = MediaType.TEXT_HTML_VALUE)
    public String deliveriesPage(Model model) {
        configureMovementList(model, MovementType.INBOUND, "Поставки", "/deliveries");
        return "movements-list";
    }

    @GetMapping(value = "/shipments", produces = MediaType.TEXT_HTML_VALUE)
    public String shipmentsPage(Model model) {
        configureMovementList(model, MovementType.OUTBOUND, "Отгрузки", "/shipments");
        return "movements-list";
    }

    @GetMapping(value = "/transfers", produces = MediaType.TEXT_HTML_VALUE)
    public String transfersPage(Model model) {
        configureMovementList(model, MovementType.TRANSFER, "Трансфер", "/transfers");
        return "movements-list";
    }

    @GetMapping(value = "/warehouse/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public String warehouseDetailsPage(@PathVariable("id") Long id) {
        return "warehouse";
    }

    @GetMapping(value = "/deliveries/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public String deliveryDetailsPage(@PathVariable("id") Long id, Model model) {
        configureMovementDetail(model, MovementType.INBOUND, "Поставка", "/deliveries");
        return "movement-detail";
    }

    @GetMapping(value = "/shipments/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public String shipmentDetailsPage(@PathVariable("id") Long id, Model model) {
        configureMovementDetail(model, MovementType.OUTBOUND, "Отгрузка", "/shipments");
        return "movement-detail";
    }

    @GetMapping(value = "/transfers/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public String transferDetailsPage(@PathVariable("id") Long id, Model model) {
        configureMovementDetail(model, MovementType.TRANSFER, "Трансфер", "/transfers");
        return "movement-detail";
    }

    private void configureMovementList(Model model, MovementType type, String title, String path) {
        model.addAttribute("movementListType", type.name());
        model.addAttribute("movementListTitle", title);
        model.addAttribute("movementListPath", path);
    }

    private void configureMovementDetail(Model model, MovementType type, String title, String path) {
        model.addAttribute("movementDetailType", type.name());
        model.addAttribute("movementDetailTitle", title);
        model.addAttribute("movementDetailPath", path);
    }
}
