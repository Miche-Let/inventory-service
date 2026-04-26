package com.michelet.inventory.presentation;

import com.michelet.inventory.application.ProductCommandService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductCommandService productCommandService;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "success", true,
            "data", productCommandService.checkHealth(),
            "message", "Inventory Command Service is running"
        );
    }
}
