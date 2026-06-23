package bolsilloApp.api.auth;

import bolsilloApp.api.auth.dto.AuthResponse;
import bolsilloApp.api.auth.dto.LoginRequest;
import bolsilloApp.api.auth.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository authRepository;

    public AuthResponse register(RegisterRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public AuthResponse login(LoginRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public AuthResponse refresh(String refreshToken) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void logout(String refreshToken) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
