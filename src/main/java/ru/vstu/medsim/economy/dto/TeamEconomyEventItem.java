package ru.vstu.medsim.economy.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TeamEconomyEventItem(
        Long eventId,
        String eventType,
        Integer stageNumber,
        BigDecimal amountDelta,
        Integer timeDelta,
        String itemName,
        Integer itemQuantityDelta,
        String message,
        LocalDateTime createdAt
) {
}
