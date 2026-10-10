package jih.backend.user.dto;

import java.time.Instant;
import java.util.UUID;
import jih.backend.user.domain.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MyProfileResponse {

    private final UUID publicId;
    private final String email;
    private final String nickname;
    private final String profileImageUrl;
    private final String bio;
    private final Instant createdAt;

    public static MyProfileResponse from(User user) {
        return new MyProfileResponse(
            user.getPublicId(),
            user.getEmail(),
            user.getNickname(),
            user.getProfileImageUrl(),
            user.getBio(),
            user.getCreatedAt()
        );
    }

}
