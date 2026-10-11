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

Status do cliente: `LEAD`, `CUSTOMER`, `INACTIVE`.

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
config/       CORS, OpenAPI, dados de demonstração
db/migration  scripts versionados do Flyway
```

## Roteiro

- [x] Fase 1: base (configuração segura, DTOs, erros padronizados, paginação, Flyway, Docker)
- [ ] Fase 2: negócios e funil Kanban com indicadores
- [ ] Fase 3: login (JWT), testes de integração, CI e demo online
- [ ] Fase 4: painel de resultados
- [ ] Fase 5: atividades, tarefas, webhooks e entrada de leads por API
