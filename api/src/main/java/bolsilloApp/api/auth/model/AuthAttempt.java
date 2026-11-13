package bolsilloApp.api.auth.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Un intento de auth a throttlear: login fallido (clave email|ip) o registro (clave ip). */
@Entity
@Table(name = "auth_attempts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthAttempt {

    public enum Kind { LOGIN, REGISTER }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(name = "throttle_key", nullable = false, length = 320)
    private String throttleKey;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;
}
