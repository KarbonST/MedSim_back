package ru.vstu.medsim.session.dto;

import jakarta.validation.constraints.NotBlank;

public record GameSessionCreateRequest(
        @NotBlank String sessionCode,
        @NotBlank String sessionName
) {
}
