package ru.vstu.medsim.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vstu.medsim.auth.dto.StaffProfileResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public StaffProfileResponse me(Authentication authentication) {
        String role = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("UNKNOWN");

        return new StaffProfileResponse(authentication.getName(), role);
    }
}
