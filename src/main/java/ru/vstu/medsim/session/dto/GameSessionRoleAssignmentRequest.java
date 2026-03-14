package ru.vstu.medsim.session.dto;

import jakarta.validation.constraints.NotBlank;

public record GameSessionRoleAssignmentRequest(
        @NotBlank String gameRole
) {
}
