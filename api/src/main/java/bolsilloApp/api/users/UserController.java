package bolsilloApp.api.users;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.users.dto.UpdateProfileRequest;
import bolsilloApp.api.users.dto.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(userService.me(currentUser.getId()));
    }

    @PatchMapping
    public ResponseEntity<UserProfileResponse> update(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.update(currentUser.getId(), request));
    }
}
