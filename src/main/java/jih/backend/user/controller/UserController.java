package jih.backend.user.controller;

import java.util.UUID;
import jih.backend.user.dto.MyProfileResponse;
import jih.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public MyProfileResponse getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        return userService.getMyProfile(UUID.fromString(jwt.getSubject()));
    }
}
