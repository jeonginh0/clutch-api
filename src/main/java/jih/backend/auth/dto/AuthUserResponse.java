package jih.backend.auth.dto;

import java.util.UUID;
import jih.backend.user.domain.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AuthUserResponse {

    private final UUID publicId;
    private final String email;
    private final String nickname;
    private final String profileImageUrl;
    private final String role;

    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(
            user.getPublicId(),
            user.getEmail(),
            user.getNickname(),
            user.getProfileImageUrl(),
            user.getRole().name());
    }
}