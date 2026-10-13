package com.jessica.crm_vendas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
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

        long demoOpenBefore = summary(demo).get("openCount").asLong();
        long unicOpenBefore = summary(admin).get("openCount").asLong();

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

        // O negócio novo entra só nos indicadores da Unic
        assertThat(summary(admin).get("openCount").asLong()).isEqualTo(unicOpenBefore + 1);
        assertThat(summary(demo).get("openCount").asLong()).isEqualTo(demoOpenBefore);
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

    // ---------- Ficha do cliente e tarefas ----------

    private long customerIdByName(String token, String name) throws Exception {
        return body(mvc.perform(get("/api/v1/customers").param("q", name).header(HttpHeaders.AUTHORIZATION, token)))
                .at("/content/0/id").asLong();
    }

    private long dealIdByTitle(String token, String title) throws Exception {
        for (JsonNode deal : body(mvc.perform(get("/api/v1/deals").header(HttpHeaders.AUTHORIZATION, token)))) {
            if (title.equals(deal.get("title").asText())) return deal.get("id").asLong();
        }
        throw new AssertionError("Negócio não encontrado: " + title);
    }

    private List<String> timelineKinds(String token, long customerId) throws Exception {
        List<String> kinds = new ArrayList<>();
        body(mvc.perform(get("/api/v1/customers/" + customerId + "/timeline").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()))
                .forEach(item -> kinds.add(item.get("kind").asText()));
        return kinds;
    }

    @Test
    void customerTimelineJoinsActivitiesStageChangesAndTasks() throws Exception {
        String demo = demo();
        long carla = customerIdByName(demo, "Carla Mendes");
        long appPedidos = dealIdByTitle(demo, "App de pedidos");

        // Os dados de demonstração já trazem histórico
        assertThat(timelineKinds(demo, carla)).contains("CALL", "EMAIL", "WHATSAPP", "STAGE_CHANGE", "TASK_DONE");

        // Registrar uma anotação: entra no topo da linha do tempo, assinada por quem registrou
        mvc.perform(post("/api/v1/customers/" + carla + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"NOTE\",\"content\":\"Prefere reunião por vídeo\",\"dealId\":" + appPedidos + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("Usuário Demo"));
        JsonNode timeline = body(mvc.perform(get("/api/v1/customers/" + carla + "/timeline").header(HttpHeaders.AUTHORIZATION, demo)));
        assertThat(timeline.get(0).get("detail").asText()).isEqualTo("Prefere reunião por vídeo");
        assertThat(timeline.get(0).get("dealTitle").asText()).isEqualTo("App de pedidos");

        // Tarefa só com o negócio fica também na ficha do cliente dele
        JsonNode task = body(mvc.perform(post("/api/v1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ligar para fechar o contrato\",\"dueAt\":\"2030-01-10T17:00:00Z\",\"dealId\":" + appPedidos + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.id").value(carla)));

        mvc.perform(get("/api/v1/tasks").param("done", "false").param("customerId", String.valueOf(carla))
                        .header(HttpHeaders.AUTHORIZATION, demo))
                .andExpect(jsonPath("$[?(@.title == 'Ligar para fechar o contrato')]").exists());

        // Concluir a tarefa: ela sai das pendentes e aparece na linha do tempo
        mvc.perform(patch("/api/v1/tasks/" + task.get("id").asLong() + "/done")
                        .header(HttpHeaders.AUTHORIZATION, demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"done\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doneAt").isNotEmpty());
        JsonNode afterDone = body(mvc.perform(get("/api/v1/customers/" + carla + "/timeline").header(HttpHeaders.AUTHORIZATION, demo)));
        assertThat(afterDone.get(0).get("kind").asText()).isEqualTo("TASK_DONE");
        assertThat(afterDone.get(0).get("detail").asText()).isEqualTo("Ligar para fechar o contrato");

        // Negócio de outro cliente não pode ser ligado à tarefa
        long paulo = customerIdByName(demo, "Paulo Siqueira");
        mvc.perform(post("/api/v1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Errada\",\"dueAt\":\"2030-01-10T17:00:00Z\",\"customerId\":" + paulo
                                + ",\"dealId\":" + appPedidos + "}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void timelineAndTasksAreIsolatedByOrganization() throws Exception {
        String demo = demo();
        String admin = admin();
        long carla = customerIdByName(demo, "Carla Mendes");
        long appPedidos = dealIdByTitle(demo, "App de pedidos");

        mvc.perform(get("/api/v1/customers/" + carla + "/timeline").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/customers/" + carla + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"NOTE\",\"content\":\"invasão\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"invasão\",\"dueAt\":\"2030-01-10T17:00:00Z\",\"dealId\":" + appPedidos + "}"))
                .andExpect(status().isNotFound());

        // A lista de tarefas da Unic não mostra as da Demo
        mvc.perform(get("/api/v1/tasks").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[?(@.title == 'Cobrar retorno da proposta de integração')]").doesNotExist());
    }

    // ---------- Painel de resultados ----------

    @Test
    void dashboardShowsTheOrganizationResultsOnly() throws Exception {
        JsonNode demo = body(mvc.perform(get("/api/v1/reports/dashboard").header(HttpHeaders.AUTHORIZATION, demo()))
                .andExpect(status().isOk()));
        assertThat(demo.get("revenueByMonth")).hasSize(6);
        assertThat(demo.at("/funnel/0/stage").asText()).isEqualTo("PROSPECT");
        assertThat(demo.at("/forecast/weightedValue").decimalValue()).isPositive();
        assertThat(demo.get("averageCycleDays").asDouble()).isPositive();

        double demoRevenue = 0;
        for (JsonNode month : demo.get("revenueByMonth")) demoRevenue += month.get("value").asDouble();
        assertThat(demoRevenue).as("a demo tem vendas nos últimos meses").isPositive();

        // A Unic não ganhou nada nestes testes: o painel dela não herda a receita da Demo
        JsonNode unic = body(mvc.perform(get("/api/v1/reports/dashboard").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk()));
        double unicRevenue = 0;
        for (JsonNode month : unic.get("revenueByMonth")) unicRevenue += month.get("value").asDouble();
        assertThat(unicRevenue).isZero();
    }

    // ---------- Importação de planilha ----------

    @Test
    void csvImportCreatesLeadsOnceAndOnlyInTheOwnOrganization() throws Exception {
        String admin = admin();
        String csv = """
                nome;whatsapp;e-mail;resultado;como conheceu
                Carla Planilha;(21) 91111-2222;carla@planilha.com;Gestão de Redes Sociais;Indicação
                Carla Planilha;21911112222;;Página Profissional (One Page);
                ;;;;
                Bruno Planilha;11922223333;;;Google
                """;
        MockMultipartFile file = new MockMultipartFile("file", "leads-antigos.csv", "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/integrations/import/csv").file(file).param("source", "WEBSITE")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").value(4))
                .andExpect(jsonPath("$.imported").value(3))
                .andExpect(jsonPath("$.newCustomers").value(2))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.issues[0].line").value(4))
                .andExpect(jsonPath("$.extraColumns[0]").value("como conheceu"));

        // Importar o mesmo arquivo de novo não duplica nada
        mvc.perform(multipart("/api/v1/integrations/import/csv").file(file).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.duplicates").value(3));

        assertThat(dealTitles(admin)).contains("Diagnóstico: Gestão de Redes Sociais", "Lead importado");
        assertThat(dealTitles(demo())).doesNotContain("Diagnóstico: Gestão de Redes Sociais");
    }

    @Test
    void csvImportExplainsWhatIsWrongWithTheFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "errado.csv", "text/csv",
                "produto,valor\nSite,1000\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/integrations/import/csv").file(file).header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("Não encontrei colunas de nome, telefone ou e-mail")));
    }

    private JsonNode summary(String token) throws Exception {
        return body(mvc.perform(get("/api/v1/reports/summary").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()));
    }
}
