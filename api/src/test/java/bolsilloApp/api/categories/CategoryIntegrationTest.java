package bolsilloApp.api.categories;

import bolsilloApp.api.categories.dto.CategoryResponse;
import bolsilloApp.api.categories.model.CategoryType;
import bolsilloApp.api.support.AbstractIntegrationTest;
import bolsilloApp.api.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryIntegrationTest extends AbstractIntegrationTest {

    // T-CAT-01
    @Test
    void listarCategoriasDevuelveLas13DelSistema() throws Exception {
        String token = registerAndGetToken();

        var result = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        List<CategoryResponse> categories = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<CategoryResponse>>() {});

        long systemCount = categories.stream().filter(CategoryResponse::isSystem).count();
        assertThat(systemCount).isEqualTo(13);
    }

    // T-CAT-02
    @Test
    void crearCategoriaCustomDevuelve201YApareceEnListado() throws Exception {
        String token = registerAndGetToken();

        var createResult = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.categoryRequest("Mi Categoria"))))
                .andExpect(status().isCreated())
                .andReturn();

        var created = objectMapper.readValue(createResult.getResponse().getContentAsString(), CategoryResponse.class);
        assertThat(created.name()).isEqualTo("Mi Categoria");
        assertThat(created.isSystem()).isFalse();

        // Verify it appears in the list
        var listResult = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        List<CategoryResponse> categories = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                new TypeReference<List<CategoryResponse>>() {});
        assertThat(categories).anyMatch(c -> c.id().equals(created.id()));
    }

    // T-CAT-03
    @Test
    void crearCategoriaIgnoraIsSystemYUserIdDelBody() throws Exception {
        String token = registerAndGetToken();

        // Send body with isSystem=true and a fake userId — server should ignore both
        String body = """
                {"name":"Hacked","emoji":"X","colorHex":"#AABBCC","type":"EXPENSE","isSystem":true,"userId":"00000000-0000-0000-0000-000000000000"}
                """;

        var result = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        var created = objectMapper.readValue(result.getResponse().getContentAsString(), CategoryResponse.class);
        assertThat(created.isSystem()).isFalse();

        // Verify in DB that user_id matches the authenticated user, not the fake one
        UUID actualUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM categories WHERE id = ?", UUID.class, created.id());
        assertThat(actualUserId).isNotNull();
        assertThat(actualUserId.toString()).isNotEqualTo("00000000-0000-0000-0000-000000000000");
    }

    // T-CAT-04
    @Test
    void colorHexInvalidoDevuelve400() throws Exception {
        String token = registerAndGetToken();

        // "rojo" — not a hex color
        String body1 = """
                {"name":"Test","emoji":"X","colorHex":"rojo","type":"EXPENSE"}
                """;
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isBadRequest());

        // "#GGG" — invalid hex chars and wrong length
        String body2 = """
                {"name":"Test","emoji":"X","colorHex":"#GGG","type":"EXPENSE"}
                """;
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isBadRequest());
    }

    // T-CAT-05
    @Test
    void nombreDuplicadoParaMismoUsuarioDevuelve409() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.categoryRequest("Duplicada"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.categoryRequest("Duplicada"))))
                .andExpect(status().isConflict());
    }

    // T-CAT-06
    @Test
    void mismoNombreEnDosUsuariosEsValido() throws Exception {
        String tokenA = registerAndGetToken("a@test.com");
        String tokenB = registerAndGetToken("b@test.com");

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.categoryRequest("Compartida"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.categoryRequest("Compartida"))))
                .andExpect(status().isCreated());
    }

    // T-CAT-07
    @Test
    void eliminarCategoriaDelSistemaDevuelve403() throws Exception {
        String token = registerAndGetToken();
        UUID systemCatId = getSystemExpenseCategoryId();

        mockMvc.perform(delete("/api/categories/" + systemCatId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // T-CAT-08
    @Test
    void eliminarCategoriaCustomConTransaccionesReasignaAOtros() throws Exception {
        String token = registerAndGetToken();

        // Create custom category
        var catResult = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.categoryRequest("Temporal"))))
                .andExpect(status().isCreated())
                .andReturn();
        var customCat = objectMapper.readValue(catResult.getResponse().getContentAsString(), CategoryResponse.class);

        // Create a transaction with the custom category
        String txBody = String.format("""
                {"amount":100,"type":"EXPENSE","description":"Test","categoryId":"%s","transactionDate":"%s"}
                """, customCat.id(), LocalDate.now());

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody))
                .andExpect(status().isCreated());

        // Delete the custom category
        mockMvc.perform(delete("/api/categories/" + customCat.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify: no transactions have null category_id
        int nullCategoryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE category_id IS NULL AND deleted_at IS NULL",
                Integer.class);
        assertThat(nullCategoryCount).isEqualTo(0);

        // Verify: the transaction was reassigned to "Otros" (EXPENSE system category)
        UUID otrosId = getSystemCategoryId("Otros");
        int otrosCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE category_id = ? AND deleted_at IS NULL",
                Integer.class, otrosId);
        assertThat(otrosCount).isGreaterThanOrEqualTo(1);
    }
}
