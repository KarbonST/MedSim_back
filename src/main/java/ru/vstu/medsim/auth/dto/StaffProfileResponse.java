package ru.vstu.medsim.auth.dto;

public record StaffProfileResponse(
        String login,
        String systemRole
) {
}
