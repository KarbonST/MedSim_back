package ru.vstu.medsim.player.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlayerSessionJoinRequest(
        @NotBlank @Size(max = 150) String displayName,
        @NotBlank @Size(max = 150) String hospitalPosition,
        @NotBlank @Size(max = 50) String sessionCode
) {
}
