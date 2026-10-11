package com.jessica.crm_vendas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Sobe a aplicação inteira com um PostgreSQL de verdade (Testcontainers):
 * valida as migrações do Flyway, a segurança e o fluxo do funil de ponta a ponta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
    "app.demo-data=true",
    "app.demo-user.enabled=true",
    "app.demo-user.email=demo@crmpro.dev",
    "app.demo-user.password=demo1234",
    "app.jwt.secret=chave-de-teste-com-pelo-menos-32-caracteres",
    "app.demo-reset-cron=-"
})
class ApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Credentials(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    record Credentials(String email, String password) {}

    @Test
    void apiRequiresLogin() throws Exception {
        mvc.perform(get("/api/v1/deals"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/deals").header(HttpHeaders.AUTHORIZATION, "Bearer token-falso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Credentials("demo@crmpro.dev", "errada"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha incorretos."));
    }

    @Test
    void healthAndDocsArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void demoUserCanWorkTheFunnel() throws Exception {
        String token = login(" Demo@CRMPro.dev ", "demo1234");

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DEMO"));

        JsonNode deals = json.readTree(mvc.perform(get("/api/v1/deals").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(deals.size()).isGreaterThanOrEqualTo(14);

        JsonNode before = summary(token);

        long proposalId = 0;
        for (JsonNode deal : deals) {
            if ("PROPOSAL".equals(deal.get("stage").asText())) {
                proposalId = deal.get("id").asLong();
                break;
            }
        }
        assertThat(proposalId).isPositive();

        mvc.perform(patch("/api/v1/deals/" + proposalId + "/stage")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"WON\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("WON"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        JsonNode after = summary(token);
        assertThat(after.get("wonThisMonthCount").asLong()).isEqualTo(before.get("wonThisMonthCount").asLong() + 1);
        assertThat(after.get("openCount").asLong()).isEqualTo(before.get("openCount").asLong() - 1);
    }

    private JsonNode summary(String token) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/reports/summary").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
