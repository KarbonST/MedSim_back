package ru.vstu.medsim.session.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GameSessionCreateRequest(
        @NotBlank
        @Size(max = 150)
        String sessionName,
        @Min(2)
        @Max(12)
        int teamCount
) {
}
