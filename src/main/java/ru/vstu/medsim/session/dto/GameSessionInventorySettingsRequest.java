package ru.vstu.medsim.session.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GameSessionInventorySettingsRequest(
        @NotEmpty List<@Valid InventoryItem> items
) {
    public record InventoryItem(
            @NotBlank String itemName,
            @NotNull @Min(0) Integer quantity
    ) {
    }
}
