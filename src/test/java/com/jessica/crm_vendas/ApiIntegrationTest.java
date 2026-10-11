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
import com.jessica.crm_vendas.integration.NetlifyTestSupport;

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
    "app.demo-reset-cron=-",
    "app.integrations.netlify.secret=" + NetlifyTestSupport.SECRET
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

    @Test
    void netlifyWebhookWithoutSignatureIsRejected() throws Exception {
        mvc.perform(post("/api/v1/integrations/netlify/forms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NetlifyTestSupport.submission("sem-assinatura", "Intrusa", "21900000000", "")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Assinatura inválida"));
    }

    @Test
    void netlifyLeadBecomesCustomerAndDealWithoutDuplicates() throws Exception {
        String first = NetlifyTestSupport.submission("envio-1", "Marina Costa", "21987654321", "");

        // Primeiro envio: cria o lead e o negócio
        JsonNode created = json.readTree(mvc.perform(post("/api/v1/integrations/netlify/forms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", NetlifyTestSupport.sign(first))
                        .content(first))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.newCustomer").value(true))
                .andReturn().getResponse().getContentAsString());

        // Reenvio do mesmo envio: ignorado
        mvc.perform(post("/api/v1/integrations/netlify/forms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", NetlifyTestSupport.sign(first))
                        .content(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.dealId").value(created.get("dealId").asLong()));

        // Mesma pessoa refazendo o quiz, com o WhatsApp formatado de outro jeito: mesmo cliente, negócio novo
        String second = NetlifyTestSupport.submission("envio-2", "Marina", "+55 (21) 98765-4321", "marina@exemplo.com");
        mvc.perform(post("/api/v1/integrations/netlify/forms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", NetlifyTestSupport.sign(second))
                        .content(second))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.newCustomer").value(false))
                .andExpect(jsonPath("$.customerId").value(created.get("customerId").asLong()));

        String token = login("demo@crmpro.dev", "demo1234");

        mvc.perform(get("/api/v1/customers/" + created.get("customerId").asLong()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Marina Costa"))
                .andExpect(jsonPath("$.phone").value("(21) 98765-4321"))
                .andExpect(jsonPath("$.email").value("marina@exemplo.com"))
                .andExpect(jsonPath("$.status").value("LEAD"));

        mvc.perform(get("/api/v1/deals/" + created.get("dealId").asLong()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Diagnóstico: Página Profissional (One Page)"))
                .andExpect(jsonPath("$.stage").value("PROSPECT"))
                .andExpect(jsonPath("$.source").value("WEBSITE"))
                .andExpect(jsonPath("$.notes").value(org.hamcrest.Matchers.containsString("Placar: Presença: 12")));

        mvc.perform(get("/api/v1/integrations/leads").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].formName").value("leads-diagnostico"))
                .andExpect(jsonPath("$[0].customerName").value("Marina Costa"));

        mvc.perform(get("/api/v1/integrations/status").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.netlifyEnabled").value(true));
    }

    @Test
    void netlifyLeadWithInvalidEmailKeepsOnlyValidData() throws Exception {
        String body = NetlifyTestSupport.submission("envio-email-invalido", "Paulo Reis", "11977776666", "nao-e-email");
        JsonNode created = json.readTree(mvc.perform(post("/api/v1/integrations/netlify/forms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", NetlifyTestSupport.sign(body))
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        String token = login("demo@crmpro.dev", "demo1234");
        mvc.perform(get("/api/v1/customers/" + created.get("customerId").asLong()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.email").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.phone").value("(11) 97777-6666"));
    }

    private JsonNode summary(String token) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/reports/summary").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
