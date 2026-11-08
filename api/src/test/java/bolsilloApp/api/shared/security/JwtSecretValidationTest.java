package bolsilloApp.api.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecretValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JwtService.class)
            .withPropertyValues("app.jwt.expiration-ms=900000");

    @Test
    void secretCortoHaceFallarElArranque() {
        // "c2hvcnQtc2VjcmV0" = "short-secret" (96 bits)
        runner.withPropertyValues("app.jwt.secret=c2hvcnQtc2VjcmV0")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("256 bits");
                });
    }

    @Test
    void secretNoBase64HaceFallarElArranque() {
        runner.withPropertyValues("app.jwt.secret=esto no es base64!!")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("Base64");
                });
    }

    @Test
    void secretVacioHaceFallarElArranque() {
        runner.withPropertyValues("app.jwt.secret=")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void secretDe256BitsArranca() {
        // 32 bytes
        runner.withPropertyValues("app.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(JwtService.class));
    }
}
