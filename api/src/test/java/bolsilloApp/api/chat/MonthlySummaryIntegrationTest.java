package bolsilloApp.api.chat;

import bolsilloApp.api.support.AbstractIntegrationTest;
import bolsilloApp.api.support.TestDataFactory;
import bolsilloApp.api.transactions.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonthlySummaryIntegrationTest extends AbstractIntegrationTest {

    @MockitoSpyBean
    AIService aiService;

    private String token;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        token = registerAndGetToken();
        userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = 'test@test.com'", UUID.class);
    }

    @Test
    void devuelveElTextoDelModeloConLosDatosDeAmbosMesesYCobraUnMensaje() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.transactionRequest(getSystemExpenseCategoryId(), new BigDecimal("4500"), TransactionType.EXPENSE, LocalDate.now()))))
                .andExpect(status().isCreated());
        doReturn(aiJson("Venís gastando $4.500 en comida.")).when(aiService).sendMessage(anyString(), anyString(), anyString(), anyBoolean());

        mockMvc.perform(post("/api/chat/monthly-summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Venís gastando $4.500 en comida."));

        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(aiService).sendMessage(eq("claude-sonnet-5-5"), anyString(), user.capture(), eq(false));
        assertThat(user.getValue()).contains("este mes").contains("el mes pasado").contains("Comida=$4500");
        assertThat(used()).isEqualTo(1);
    }

    @Test
    void sinCuotaDevuelve429SinLlamarALaIA() throws Exception {
        jdbcTemplate.update("UPDATE users SET ai_messages_used = 100 WHERE id = ?", userId);

        mockMvc.perform(post("/api/chat/monthly-summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests());

        verify(aiService, never()).sendMessage(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void siLaIAFallaDevuelve503YNoCobra() throws Exception {
        doThrow(new RuntimeException("boom")).when(aiService).sendMessage(anyString(), anyString(), anyString(), anyBoolean());

        mockMvc.perform(post("/api/chat/monthly-summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());

        assertThat(used()).isZero();
    }

    private int used() {
        return jdbcTemplate.queryForObject("SELECT ai_messages_used FROM users WHERE id = ?", Integer.class, userId);
    }

    private String aiJson(String text) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "content", List.of(Map.of("type", "text", "text", text)),
                "usage", Map.of("input_tokens", 20, "output_tokens", 10)));
    }
}
