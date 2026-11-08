package bolsilloApp.api.support;

import bolsilloApp.api.auth.dto.AuthResponse;
import bolsilloApp.api.auth.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.TimeZone;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bolsillo_test");

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Exchange rates: nunca llamar a la API real en tests (ni al arrancar ni por cron)
        registry.add("exchange-rates.load-on-startup", () -> "false");
        registry.add("exchange-rates.cron", () -> "-");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM user_aliases");
        jdbcTemplate.execute("DELETE FROM chat_messages");
        jdbcTemplate.execute("DELETE FROM transactions");
        jdbcTemplate.execute("DELETE FROM purchases");
        jdbcTemplate.execute("DELETE FROM debts");
        jdbcTemplate.execute("DELETE FROM refresh_tokens");
        jdbcTemplate.execute("DELETE FROM exchange_rates");
        jdbcTemplate.execute("DELETE FROM categories WHERE user_id IS NOT NULL");
        jdbcTemplate.execute("DELETE FROM users");
    }

    // -- Auth helpers --

    protected AuthResponse registerUser(String email, String password, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, password, displayName, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    protected AuthResponse registerDefaultUser() throws Exception {
        return registerUser("test@test.com", "password123", "Test User");
    }

    protected String registerAndGetToken() throws Exception {
        return registerDefaultUser().accessToken();
    }

    protected String registerAndGetToken(String email) throws Exception {
        return registerUser(email, "password123", "User " + email.split("@")[0]).accessToken();
    }

    // -- Category helpers --

    protected UUID getSystemCategoryId(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE name = ? AND is_system = true",
                UUID.class, name);
    }

    protected UUID getSystemExpenseCategoryId() {
        return getSystemCategoryId("Comida");
    }

    protected UUID getSystemIncomeCategoryId() {
        return getSystemCategoryId("Sueldo");
    }

    // -- JSON helper --

    protected String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
