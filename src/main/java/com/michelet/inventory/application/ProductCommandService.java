package com.michelet.inventory.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductCommandService {

    @Transactional(readOnly = true)
    public String checkHealth() {
        return "Inventory Command Service is Healthy";
    }
}