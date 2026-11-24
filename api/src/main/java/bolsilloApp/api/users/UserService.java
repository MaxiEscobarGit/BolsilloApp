package bolsilloApp.api.users;

import bolsilloApp.api.auth.AuthRepository;
import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.chat.ChatService;
import bolsilloApp.api.shared.exception.ApiException;
import bolsilloApp.api.users.dto.UpdateProfileRequest;
import bolsilloApp.api.users.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AuthRepository authRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse me(UUID userId) {
        return toResponse(find(userId));
    }

    @Transactional
    public UserProfileResponse update(UUID userId, UpdateProfileRequest request) {
        User user = find(userId);
        if (request.defaultCurrency() != null) user.setDefaultCurrency(request.defaultCurrency());
        if (request.preferredExchangeSource() != null) user.setPreferredExchangeSource(request.preferredExchangeSource());
        return toResponse(authRepository.save(user));
    }

    private User find(UUID userId) {
        return authRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", "NOT_FOUND", 404));
    }

    // The chat resets the counter lazily on the next message once a month has passed since the last reset,
    // so a stale counter is shown as 0 and the next reset as one month after that.
    private UserProfileResponse toResponse(User u) {
        LocalDateTime now = LocalDateTime.now();
        boolean expired = u.getAiMessagesResetAt().isBefore(now.minusMonths(1));
        return new UserProfileResponse(
                u.getId(), u.getEmail(), u.getDisplayName(), u.getDefaultCurrency(), u.getPreferredExchangeSource(),
                expired ? 0 : u.getAiMessagesUsed(),
                ChatService.MAX_AI_MESSAGES_PER_MONTH,
                expired ? now.plusMonths(1) : u.getAiMessagesResetAt().plusMonths(1));
    }
}
