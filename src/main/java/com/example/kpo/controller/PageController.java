package com.example.kpo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping({"/", "/login"})
    public String loginPage() {
        return "redirect:/login.html";
    }

    @GetMapping("/dashboard")
    public String dashboardStub() {
        return "redirect:/dashboard.html";
    }
}
