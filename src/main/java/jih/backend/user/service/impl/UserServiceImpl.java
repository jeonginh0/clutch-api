package jih.backend.user.service.impl;

import java.util.UUID;
import jih.backend.global.error.BusinessException;
import jih.backend.global.error.ErrorCode;
import jih.backend.user.dto.MyProfileResponse;
import jih.backend.user.repository.UserRepository;
import jih.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public MyProfileResponse getMyProfile(UUID publicId) {
        return userRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .map(MyProfileResponse::from)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
