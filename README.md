# CRM Vendas · API

API REST de um CRM de vendas: clientes, funil de negócios e integrações com outras plataformas.
Frontend em [crm-frontend](https://github.com/jessicaccl/crm-frontend).

**Stack:** Java 17 · Spring Boot 3.5 · Spring Data JPA · PostgreSQL · Flyway · springdoc (Swagger) · Docker

## Como rodar

### Opção 1: Docker (banco + API)

```bash
cp .env.example .env      # defina DB_PASSWORD
docker compose up --build
```

API em http://localhost:8080 e documentação em http://localhost:8080/docs.

### Opção 2: PostgreSQL instalado na máquina

```bash
cp .env.example .env      # ajuste DB_URL, DB_USERNAME e DB_PASSWORD
./mvnw spring-boot:run
```

O Flyway cria e atualiza as tabelas sozinho. Com `DEMO_DATA=true`, um banco vazio recebe clientes de exemplo.

### Testes

```bash
./mvnw test
```

## Endpoints (v1)

| Método | Rota | O que faz |
|---|---|---|
| GET | `/api/v1/customers?q=&status=&page=&size=&sort=` | Lista com busca, filtro e paginação |
| GET | `/api/v1/customers/{id}` | Detalhe |
| POST | `/api/v1/customers` | Cadastra (201 + `Location`) |
| PUT | `/api/v1/customers/{id}` | Atualiza |
| DELETE | `/api/v1/customers/{id}` | Exclui (204) |

| GET | `/api/v1/deals?q=&customerId=` | Lista negócios (alimenta o Kanban) |
| GET | `/api/v1/deals/{id}` | Detalhe |
| POST | `/api/v1/deals` | Cria um negócio |
| PUT | `/api/v1/deals/{id}` | Atualiza |
| PATCH | `/api/v1/deals/{id}/stage` | Move de etapa, grava o histórico e publica o evento |
| DELETE | `/api/v1/deals/{id}` | Exclui |
| GET | `/api/v1/reports/summary` | Indicadores: em aberto, ganho no mês, conversão em 90 dias, parados |

Status do cliente: `LEAD`, `CUSTOMER`, `INACTIVE`.
Etapas do negócio: `PROSPECT`, `QUALIFIED`, `PROPOSAL`, `WON`, `LOST` (perder exige `lostReason`, senão responde 422).

## Modelo

```
Cliente 1 ── N Negócio 1 ── N Mudança de etapa
```

Toda mudança de etapa gera um registro em `tb_deal_stage_changes` e publica um `DealStageChangedEvent`.
Esse evento é o ponto de extensão para integrações: na fase 5 um listener envia webhooks com ele.

Erros seguem o padrão [ProblemDetail (RFC 9457)](https://www.rfc-editor.org/rfc/rfc9457):

```json
{
  "status": 400,
  "title": "Dados inválidos",
  "detail": "Corrija os campos destacados.",
  "errors": { "name": "Informe o nome" }
}
```

## Estrutura

```
controller/   rotas HTTP
service/      regras de negócio
repository/   acesso a dados e filtros (Specifications)
model/        entidades JPA
dto/          contratos de entrada e saída
exception/    erros de domínio e handler global
event/        eventos de domínio (mudança de etapa)
config/       CORS, OpenAPI, relógio/fuso, dados de demonstração
db/migration  scripts versionados do Flyway
```

## Roteiro

- [x] Fase 1: base (configuração segura, DTOs, erros padronizados, paginação, Flyway, Docker)
- [x] Fase 2: negócios, histórico de etapas e indicadores do funil
- [ ] Fase 3: login (JWT), testes de integração, CI e demo online
- [ ] Fase 4: painel de resultados
- [ ] Fase 5: atividades, tarefas, webhooks e entrada de leads por API
