# CRM Vendas · API

[![CI](https://github.com/jessicaccl/crm-vendas-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/jessicaccl/crm-vendas-backend/actions/workflows/ci.yml)

API REST de um CRM de vendas **multiempresa**: clientes, funil de negócios, ficha do cliente com linha do tempo,
tarefas, painel de resultados e entrada automática de leads (webhook do Netlify e planilhas CSV).
Em uso real pela Unic Studio, recebendo os leads do quiz de diagnóstico do site.
Frontend em [crm-frontend](https://github.com/jessicaccl/crm-frontend).

**Demo online:** https://crm-pro-jessica.vercel.app (clique em "Entrar como demo")
**Documentação da API:** https://crm-vendas-api-a5xa.onrender.com/docs
*(plano gratuito: o primeiro acesso pode levar até 1 minuto enquanto o servidor acorda)*

📄 **Estudo de caso de produto:** [problema, decisões e próximos passos](docs/ESTUDO-DE-CASO.md)

![Funil de vendas](https://raw.githubusercontent.com/jessicaccl/crm-frontend/main/docs/screenshots/funil-arrastar.gif)

| Painel de resultados | Ficha do cliente |
|---|---|
| ![Painel](https://raw.githubusercontent.com/jessicaccl/crm-frontend/main/docs/screenshots/15-painel.png) | ![Ficha](https://raw.githubusercontent.com/jessicaccl/crm-frontend/main/docs/screenshots/13-ficha-do-cliente.png) |

**Stack:** Java 17 · Spring Boot 3.5 · Spring Security (JWT) · Spring Data JPA · PostgreSQL · Flyway · springdoc (Swagger) · Testcontainers · Docker

## Como rodar

### Opção 1: Docker (banco + API)

```bash
cp .env.example .env      # defina DB_PASSWORD e JWT_SECRET
docker compose up --build
```

API em http://localhost:8080 e documentação em http://localhost:8080/docs.

### Opção 2: PostgreSQL instalado na máquina

```bash
cp .env.example .env      # ajuste DB_URL, DB_USERNAME e DB_PASSWORD
./mvnw spring-boot:run
```

O Flyway cria e atualiza as tabelas sozinho. Com `DEMO_DATA=true`, um banco vazio recebe clientes e negócios de exemplo.

**Login de demonstração:** `demo@crmpro.dev` / `demo1234`. Para criar sua conta de administradora, defina `ADMIN_EMAIL` e `ADMIN_PASSWORD`.

### Testes

```bash
./mvnw verify
```

Roda os testes de unidade e os de integração, que sobem um PostgreSQL real com Testcontainers (precisa do Docker ligado).
No GitHub, o mesmo comando roda a cada push e pull request.

## Autenticação

A API é stateless: `POST /api/v1/auth/login` devolve um JWT, e as demais rotas exigem `Authorization: Bearer <token>`.
No Swagger, faça login, copie o `token` e clique em **Authorize**.

![Swagger da API](docs/swagger.png)

São públicos apenas o login, o `/actuator/health` e a documentação.

## Endpoints (v1)

| Método | Rota | O que faz |
|---|---|---|
| POST | `/api/v1/auth/login` | Login, devolve o token |
| GET | `/api/v1/auth/me` | Usuário logado |
| GET | `/api/v1/customers?q=&status=&page=&size=&sort=` | Lista clientes com busca, filtro e paginação |
| GET | `/api/v1/customers/{id}` | Detalhe |
| POST | `/api/v1/customers` | Cadastra (201 + `Location`) |
| PUT | `/api/v1/customers/{id}` | Atualiza |
| DELETE | `/api/v1/customers/{id}` | Exclui (409 se o cliente tiver negócios) |
| GET | `/api/v1/deals?q=&customerId=` | Lista negócios (alimenta o Kanban) |
| GET | `/api/v1/deals/{id}` | Detalhe |
| POST | `/api/v1/deals` | Cria um negócio |
| PUT | `/api/v1/deals/{id}` | Atualiza |
| PATCH | `/api/v1/deals/{id}/stage` | Move de etapa, grava o histórico e publica o evento |
| DELETE | `/api/v1/deals/{id}` | Exclui |
| GET | `/api/v1/reports/summary` | Indicadores: em aberto, ganho no mês, conversão em 90 dias, parados |
| GET | `/api/v1/reports/dashboard` | Painel: receita por mês, funil de conversão, origens, previsão ponderada, ciclo médio, ticket médio, motivos de perda, parados |
| GET | `/api/v1/customers/{id}/timeline` | Linha do tempo: atividades, mudanças de etapa, leads recebidos e tarefas concluídas |
| POST | `/api/v1/customers/{id}/activities` | Registra anotação, ligação, WhatsApp, e-mail ou reunião |
| DELETE | `/api/v1/activities/{id}` | Exclui uma atividade |
| GET | `/api/v1/tasks?done=&customerId=&dealId=` | Lista tarefas (pendentes pelo prazo, concluídas pelas recentes) |
| POST / PUT / DELETE | `/api/v1/tasks[/{id}]` | Cria, edita e exclui tarefas |
| PATCH | `/api/v1/tasks/{id}/done` | Conclui ou reabre uma tarefa |
| POST | `/api/v1/integrations/netlify/forms/{organização}` | Webhook do Netlify Forms (autenticado pela assinatura da organização) |
| POST | `/api/v1/integrations/netlify/secret` | Gera a chave do webhook da organização (administradoras) |
| POST | `/api/v1/integrations/import/csv?source=` | Importa uma planilha CSV de leads (multipart, até 2 MB / 2.000 linhas) |
| GET | `/api/v1/integrations/leads` | Últimos leads recebidos pela organização |
| GET | `/api/v1/integrations/status` | Situação das integrações da organização |

Todas as rotas logadas retornam **apenas dados da organização do usuário**.

Status do cliente: `LEAD`, `CUSTOMER`, `INACTIVE`.
Etapas do negócio: `PROSPECT`, `QUALIFIED`, `PROPOSAL`, `WON`, `LOST` (perder exige `lostReason`, senão responde 422).

Erros seguem o padrão [ProblemDetail (RFC 9457)](https://www.rfc-editor.org/rfc/rfc9457):

```json
{
  "status": 400,
  "title": "Dados inválidos",
  "detail": "Corrija os campos destacados.",
  "errors": { "name": "Informe o nome" }
}
```

## Modelo

```
Organização 1 ── N Usuário
            1 ── N Cliente 1 ── N Negócio 1 ── N Mudança de etapa
                           1 ── N Atividade (anotação, ligação, WhatsApp, e-mail, reunião)
                           1 ── N Tarefa (opcionalmente ligada a um negócio)
            1 ── N Lead recebido (integração) ──> Cliente + Negócio
```

Toda mudança de etapa gera um registro em `tb_deal_stage_changes` e publica um `DealStageChangedEvent`.
Esse histórico alimenta o funil de conversão e o ciclo médio do painel, e a linha do tempo do cliente.
O evento é o ponto de extensão para webhooks de saída.

## Painel de resultados

`GET /reports/dashboard` calcula, para a organização logada:

| Métrica | Como é calculada |
|---|---|
| Receita por mês | soma dos negócios ganhos em cada um dos últimos 6 meses (fuso `APP_TIMEZONE`) |
| Funil de conversão | dos negócios que entraram nos últimos 90 dias, quantos chegaram a cada etapa ou além (pelo histórico) |
| Origem | negócios por origem, com quantos foram ganhos e o valor |
| Previsão ponderada | valor em aberto × chance da etapa (Prospecto 10%, Qualificado 30%, Proposta 60%) |
| Ciclo médio | dias da entrada no funil até o ganho, nos ganhos dos últimos 90 dias |
| Motivos de perda | os mais comuns nos últimos 90 dias |

Os cálculos ficam em `DashboardService.build`, uma função pura testada com dados controlados.

## Multiempresa (multi-tenant)

Um único deploy atende várias empresas, com os dados separados por **organização**:

- **O token de login carrega a organização** (claim `org`). O `TenantContext` lê esse valor e **toda consulta**
  (listas, buscas, indicadores, edição, exclusão) filtra por ele.
- **Dados de outra organização respondem 404**, como se não existissem. Também não dá para ligar um negócio
  a um cliente de outra empresa.
- **E-mail de cliente é único por organização** (`unique (organization_id, email)`).
- **Webhooks não têm login:** a organização vem do endereço (`/netlify/forms/{slug}`) e é confirmada pela
  assinatura com a chave daquela organização.
- **A organização Demo** tem dados fictícios e login público, não aceita integrações, e só ela é restaurada
  pelo `DEMO_RESET_CRON`.
- **Cobertura:** o `ApiIntegrationTest` verifica o isolamento com PostgreSQL real. A Demo não lê, não edita,
  não move e não referencia dados da Unic, e os indicadores de cada empresa contam só os próprios negócios.

As organizações iniciais são criadas na subida da API: `demo` (usuário `DEMO_USER_*`) e a da administradora
(`ADMIN_ORG_NAME` / `ADMIN_ORG_SLUG`, usuária `ADMIN_EMAIL` / `ADMIN_PASSWORD`).

## Integração: leads do formulário do Netlify

O [quiz de diagnóstico da Unic Studio](https://github.com/jessicaccl/unic-quiz-diagnostico) grava cada resposta no
Netlify Forms. Um **outgoing webhook** do Netlify envia o envio para o CRM, que:

1. **Confere a assinatura** `X-Webhook-Signature`: um JWT HS256 com `iss=netlify` e o `sha256` do corpo,
   assinado com a chave que a organização gerou na tela **Integrações** e colou no Netlify. Sem ela, responde 401.
2. **Ignora reenvios** do mesmo envio (chave única `organização + provider + id do envio`).
3. **Encontra ou cria o contato** pelo e-mail ou pelo WhatsApp, ignorando a formatação do número,
   e completa o cadastro sem sobrescrever o que já existe.
4. **Abre um negócio** "Diagnóstico: \<resultado\>" em Prospecto, com o placar nas observações,
   passando pelo mesmo fluxo do funil (histórico de etapas e evento).
5. **Guarda o envio original** em `tb_inbound_leads` (JSONB) para auditoria.

```
Quiz (React) ─▶ Netlify Forms ─webhook assinado─▶ API /netlify/forms/unic ─▶ Cliente (Lead) + Negócio em Prospecto
```

Enquanto a organização não gera a chave, o webhook dela responde 404.

### Importação de planilha (CSV)

Para trazer leads antigos (export do Netlify Forms, Excel, Google Sheets), a mesma regra do webhook é usada linha a linha:

- **Colunas reconhecidas pelo nome**, em português ou inglês, com ou sem acento: `nome`/`name`,
  `telefone`/`whatsapp`/`phone`, `e-mail`, `empresa`, `resultado`, `placar`, `data`/`created_at`, `id`.
  As outras colunas vão para as observações do negócio; colunas técnicas (`ip`, `user_agent`…) são ignoradas.
- **Formatos**: vírgula ou ponto e vírgula, UTF-8 (com ou sem BOM) ou o padrão do Excel no Windows.
- **Idempotente**: cada linha tem uma identidade (o `id` da planilha ou um hash dos dados). Importar o mesmo
  arquivo de novo não cria nada repetido.
- **Relatório**: linhas lidas, importadas, contatos novos, já importadas, e quais linhas foram ignoradas e por quê.

O parser fica em `CsvLeadParser` e é testado com os formatos do Netlify e do Excel em português.

## Deploy

Banco no **Neon**, API no **Render** (Docker, via [`render.yaml`](render.yaml)) e frontend na **Vercel**, todos no plano gratuito.
O passo a passo com prints está em **[docs/DEPLOY.md](docs/DEPLOY.md)**.

Na demo online, `DEMO_RESET_CRON=0 0 4 * * *` restaura os dados de exemplo todo dia às 4h.

## Estrutura

```
controller/   rotas HTTP
service/      regras de negócio
repository/   acesso a dados e filtros (Specifications)
model/        entidades JPA
dto/          contratos de entrada e saída
security/     JWT, organização do pedido (TenantContext), regras de acesso e respostas 401
integration/  entrada de leads externos (webhook do Netlify, planilhas CSV, assinatura, deduplicação)
exception/    erros de domínio e handler global
event/        eventos de domínio (mudança de etapa)
config/       CORS, OpenAPI, relógio/fuso, usuários iniciais, dados de demonstração
db/migration  scripts versionados do Flyway
```

## Roteiro

- [x] Fase 1: base (configuração segura, DTOs, erros padronizados, paginação, Flyway, Docker)
- [x] Fase 2: negócios, histórico de etapas e indicadores do funil
- [x] Fase 3: login (JWT), testes de integração, CI, deploy e demo online
- [x] Fase 4: integração com o formulário do Netlify e importação de planilhas CSV
- [x] Fase 5: multiempresa (organizações isoladas em um único deploy)
- [x] Fase 6: painel de resultados
- [x] Fase 7: ficha do cliente, linha do tempo e tarefas

**Próximos passos possíveis:** webhooks de saída (o evento de mudança de etapa já existe), cadastro de novas
empresas e convites de usuários pela própria tela, anexos na ficha do cliente.
