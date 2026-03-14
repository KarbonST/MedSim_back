package ru.vstu.medsim.session.dto;

import jakarta.validation.constraints.NotNull;

public record GameSessionTeamAssignmentRequest(
        @NotNull
        Long teamId
) {
}
