package com.michelet.inventory.application.dto;

import java.util.UUID;

public record ProductStatusChangedEvent(
    UUID productId,
    String newStatus
) {
}
