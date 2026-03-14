package ru.vstu.medsim.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GameSessionTeamRenameRequest(
        @NotBlank
        @Size(max = 150)
        String teamName
) {
}
