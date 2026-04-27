package com.michelet.inventory.presentation;

import com.michelet.common.response.ApiResponse;
import com.michelet.inventory.application.ProductCommandService;
import com.michelet.inventory.application.dto.ProductResult;
import com.michelet.inventory.presentation.dto.CreateProductRequest;
import com.michelet.inventory.presentation.dto.ProductResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductCommandService productCommandService;

    @GetMapping("/health")
    public ApiResponse<String> checkHealth() {
        return ApiResponse.ok(productCommandService.checkHealth());
    }

    @PostMapping
    public ApiResponse<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductResult result = productCommandService.createProduct(request.toCommand());
        return ApiResponse.ok(new ProductResponse(result.productId()));
    }
}
