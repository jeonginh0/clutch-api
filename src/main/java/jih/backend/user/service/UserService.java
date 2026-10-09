package jih.backend.user.service;

import java.util.UUID;
import jih.backend.user.dto.MyProfileResponse;

public interface UserService {

    MyProfileResponse getMyProfile(UUID publicId);
}
