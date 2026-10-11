package com.jessica.crm_vendas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jessica.crm_vendas.integration.NetlifyTestSupport;

/**
 * Sobe a aplicação inteira com um PostgreSQL de verdade (Testcontainers):
 * valida as migrações do Flyway, a segurança, o isolamento entre organizações
 * e os fluxos do funil e do webhook de ponta a ponta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
    "app.demo-data=true",
    "app.demo-user.enabled=true",
    "app.demo-user.email=demo@crmpro.dev",
    "app.demo-user.password=demo1234",
    "app.admin.email=admin@unic.test",
    "app.admin.password=senha-da-admin-123",
    "app.admin.org-name=Unic Studio",
    "app.admin.org-slug=unic",
    "app.jwt.secret=chave-de-teste-com-pelo-menos-32-caracteres",
    "app.demo-reset-cron=-"
})
class ApiIntegrationTest {

    private static final String WEBHOOK = "/api/v1/integrations/netlify/forms/";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    record Credentials(String email, String password) {}

    private JsonNode loginResponse(String email, String password) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Credentials(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String token(String email, String password) throws Exception {
        return "Bearer " + loginResponse(email, password).get("token").asText();
    }

    private String demo() throws Exception {
        return token("demo@crmpro.dev", "demo1234");
    }

    private String admin() throws Exception {
        return token("admin@unic.test", "senha-da-admin-123");
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private List<String> dealTitles(String token) throws Exception {
        List<String> titles = new ArrayList<>();
        body(mvc.perform(get("/api/v1/deals").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk()))
                .forEach(deal -> titles.add(deal.get("title").asText()));
        return titles;
    }

    private String newNetlifySecret(String adminToken) throws Exception {
        return body(mvc.perform(post("/api/v1/integrations/netlify/secret").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())).get("secret").asText();
    }

    private ResultActions sendWebhook(String slug, String payload, String secret) throws Exception {
        return mvc.perform(post(WEBHOOK + slug)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Webhook-Signature", NetlifyTestSupport.sign(payload, secret, "netlify"))
                .content(payload));
    }

    // ---------- Segurança ----------

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

    // ---------- Multiempresa ----------

    @Test
    void loginTellsWhichOrganizationTheUserBelongsTo() throws Exception {
        JsonNode demo = loginResponse(" Demo@CRMPro.dev ", "demo1234");
        assertThat(demo.at("/user/organization/slug").asText()).isEqualTo("demo");
        assertThat(demo.at("/user/organization/demo").asBoolean()).isTrue();

        JsonNode admin = loginResponse("admin@unic.test", "senha-da-admin-123");
        assertThat(admin.at("/user/role").asText()).isEqualTo("ADMIN");
        assertThat(admin.at("/user/organization/name").asText()).isEqualTo("Unic Studio");
        assertThat(admin.at("/user/organization/demo").asBoolean()).isFalse();
    }

    @Test
    void organizationsNeverSeeEachOthersData() throws Exception {
        String admin = admin();
        String demo = demo();

        // A Unic não enxerga os dados fictícios da Demo
        assertThat(dealTitles(admin)).doesNotContain("App de pedidos", "E-commerce completo");

        // A Unic cadastra um cliente e um negócio
        long customerId = body(mvc.perform(post("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cliente Sigiloso\",\"email\":\"rafa@sigilo.com\"}"))
                .andExpect(status().isCreated())).get("id").asLong();
        long dealId = body(mvc.perform(post("/api/v1/deals")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Contrato sigiloso\",\"customerId\":" + customerId + ",\"value\":5000}"))
                .andExpect(status().isCreated())).get("id").asLong();

        // A Demo não vê, não edita e não move nada da Unic (responde como se não existisse)
        assertThat(dealTitles(demo)).doesNotContain("Contrato sigiloso");
        mvc.perform(get("/api/v1/customers/" + customerId).header(HttpHeaders.AUTHORIZATION, demo))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/deals/" + dealId).header(HttpHeaders.AUTHORIZATION, demo))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/deals/" + dealId + "/stage")
                        .header(HttpHeaders.AUTHORIZATION, demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"WON\"}"))
                .andExpect(status().isNotFound());

        // Nem consegue criar um negócio apontando para o cliente da Unic
        mvc.perform(post("/api/v1/deals")
                        .header(HttpHeaders.AUTHORIZATION, demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Invasão\",\"customerId\":" + customerId + ",\"value\":1}"))
                .andExpect(status().isNotFound());

        // O mesmo e-mail pode existir em outra organização
        mvc.perform(post("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Outro Rafa\",\"email\":\"rafa@sigilo.com\"}"))
                .andExpect(status().isCreated());

        // Os indicadores da Unic contam só os negócios dela
        long demoOpen = body(mvc.perform(get("/api/v1/reports/summary").header(HttpHeaders.AUTHORIZATION, demo)))
                .get("openCount").asLong();
        long unicOpen = body(mvc.perform(get("/api/v1/reports/summary").header(HttpHeaders.AUTHORIZATION, admin)))
                .get("openCount").asLong();
        assertThat(demoOpen).isGreaterThanOrEqualTo(7);
        assertThat(unicOpen).isLessThan(demoOpen);
    }

    @Test
    void demoUserCanWorkTheFunnel() throws Exception {
        String token = demo();

        JsonNode deals = body(mvc.perform(get("/api/v1/deals").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()));
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

    // ---------- Integração Netlify ----------

    @Test
    void demoCannotConfigureIntegrations() throws Exception {
        String demo = demo();
        mvc.perform(get("/api/v1/integrations/status").header(HttpHeaders.AUTHORIZATION, demo))
                .andExpect(jsonPath("$.available").value(false));
        mvc.perform(post("/api/v1/integrations/netlify/secret").header(HttpHeaders.AUTHORIZATION, demo))
                .andExpect(status().isForbidden());
        // A organização demo nunca recebe webhooks
        String payload = NetlifyTestSupport.submission("demo-1", "Alguém", "21900000000", "");
        sendWebhook("demo", payload, NetlifyTestSupport.SECRET).andExpect(status().isNotFound());
    }

    @Test
    void netlifyWebhookRequiresTheOrganizationSignature() throws Exception {
        String secret = newNetlifySecret(admin());
        String payload = NetlifyTestSupport.submission("sem-assinatura", "Intrusa", "21900000000", "");

        mvc.perform(post(WEBHOOK + "unic").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Assinatura inválida"));
        sendWebhook("unic", payload, "chave-de-outra-empresa-com-32-caracteres").andExpect(status().isUnauthorized());

        // Trocar a chave invalida a anterior
        String newSecret = newNetlifySecret(admin());
        sendWebhook("unic", payload, secret).andExpect(status().isUnauthorized());
        sendWebhook("unic", payload, newSecret).andExpect(status().isCreated());
    }

    @Test
    void netlifyLeadBecomesCustomerAndDealWithoutDuplicates() throws Exception {
        String admin = admin();
        String secret = newNetlifySecret(admin);
        String first = NetlifyTestSupport.submission("envio-1", "Marina Costa", "21987654321", "");

        JsonNode created = body(sendWebhook("unic", first, secret)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.newCustomer").value(true)));

        // Reenvio do mesmo envio: ignorado
        sendWebhook("unic", first, secret)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.dealId").value(created.get("dealId").asLong()));

        // Mesma pessoa refazendo o quiz, com o WhatsApp formatado de outro jeito: mesmo cliente, negócio novo
        String second = NetlifyTestSupport.submission("envio-2", "Marina", "+55 (21) 98765-4321", "marina@exemplo.com");
        sendWebhook("unic", second, secret)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.newCustomer").value(false))
                .andExpect(jsonPath("$.customerId").value(created.get("customerId").asLong()));

        mvc.perform(get("/api/v1/customers/" + created.get("customerId").asLong()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Marina Costa"))
                .andExpect(jsonPath("$.phone").value("(21) 98765-4321"))
                .andExpect(jsonPath("$.email").value("marina@exemplo.com"))
                .andExpect(jsonPath("$.status").value("LEAD"));

        mvc.perform(get("/api/v1/deals/" + created.get("dealId").asLong()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Diagnóstico: Página Profissional (One Page)"))
                .andExpect(jsonPath("$.stage").value("PROSPECT"))
                .andExpect(jsonPath("$.source").value("WEBSITE"))
                .andExpect(jsonPath("$.notes").value(containsString("Placar: Presença: 12")));

        mvc.perform(get("/api/v1/integrations/leads").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].formName").value("leads-diagnostico"))
                .andExpect(jsonPath("$[0].customerName").value("Marina Costa"));

        mvc.perform(get("/api/v1/integrations/status").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.netlifyConfigured").value(true))
                .andExpect(jsonPath("$.netlifyWebhookPath").value(WEBHOOK + "unic"));

        // O lead da Unic não aparece para a Demo
        assertThat(dealTitles(demo())).doesNotContain("Diagnóstico: Página Profissional (One Page)");
        mvc.perform(get("/api/v1/integrations/leads").header(HttpHeaders.AUTHORIZATION, demo()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void netlifyLeadWithInvalidEmailKeepsOnlyValidData() throws Exception {
        String admin = admin();
        String secret = newNetlifySecret(admin);
        String payload = NetlifyTestSupport.submission("envio-email-invalido", "Paulo Reis", "11977776666", "nao-e-email");

        JsonNode created = body(sendWebhook("unic", payload, secret).andExpect(status().isCreated()));

        mvc.perform(get("/api/v1/customers/" + created.get("customerId").asLong()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.email").value(nullValue()))
                .andExpect(jsonPath("$.phone").value("(11) 97777-6666"));
    }

    private JsonNode summary(String token) throws Exception {
        return body(mvc.perform(get("/api/v1/reports/summary").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()));
    }
}
