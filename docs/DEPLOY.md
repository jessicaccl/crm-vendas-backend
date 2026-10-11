# Guia de deploy

Como o CRM Pro foi colocado no ar usando só planos gratuitos:

| Parte | Serviço | Endereço |
|---|---|---|
| Banco PostgreSQL | [Neon](https://neon.tech) | (privado) |
| API Java | [Render](https://render.com), via Docker | https://crm-vendas-api-a5xa.onrender.com |
| Frontend React | [Vercel](https://vercel.com) | https://crm-pro-jessica.vercel.app |

```
Navegador ──> Vercel (React) ──HTTPS + JWT──> Render (Spring Boot) ──SSL──> Neon (PostgreSQL)
```

A ordem importa: **banco → API → frontend → liberar o frontend na API (CORS)**.

---

## 1. Banco no Neon

1. Crie um projeto (aqui, `CRM-PRO`) na mesma região onde a API vai rodar. Usamos **AWS US East 2 (Ohio)**.
2. Clique em **Conecte-se**, **desligue "Connection pooling"** (o Flyway e o pool da API usam conexão direta) e copie a string de conexão.

![Projeto no Neon](deploy/01-neon-projeto.png)

A string do Neon tem o formato `postgresql://USUARIO:SENHA@HOST/BANCO?sslmode=require`.
A API Java precisa dela separada em três partes:

| Variável | Valor |
|---|---|
| `DB_URL` | `jdbc:postgresql://HOST/BANCO?sslmode=require` (sem usuário e senha) |
| `DB_USERNAME` | `USUARIO` |
| `DB_PASSWORD` | `SENHA` |

Se a string vier com `&channel_binding=require` no final, remova esse trecho; o driver JDBC usa outro nome para essa opção.

## 2. API no Render

O repositório tem um [`render.yaml`](../render.yaml) (Blueprint), que já configura Docker, região, health check (`/actuator/health`), fuso e gera o `JWT_SECRET` automaticamente.

1. **New → Blueprint** e escolha este repositório (branch `main`).

![Blueprint no Render](deploy/02-render-blueprint.png)

2. Preencha as variáveis pedidas. As marcadas com `sync: false` no `render.yaml` nunca ficam no Git:

![Variáveis do Blueprint](deploy/03-render-variaveis.png)

| Variável | O que é |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | dados do Neon (passo 1) |
| `CORS_ALLOWED_ORIGINS` | endereço do frontend; use `http://localhost:5173` até ter o da Vercel |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | login da sua conta de administradora no CRM (crie uma senha nova) |

3. Clique em **Deploy Blueprint**. O primeiro build (Maven + Docker) leva de 5 a 15 minutos:

![Sync do Blueprint](deploy/04-render-sync.png)

4. Quando aparecer **Live**, o endereço da API fica no topo da página:

![Serviço no ar](deploy/05-render-live.png)

Para testar: `https://SEU-SERVICO.onrender.com/docs` abre o Swagger, e o login de demonstração é `demo@crmpro.dev` / `demo1234`.

> **Plano gratuito:** a API "dorme" após ~15 minutos sem uso e o primeiro acesso leva até 1 minuto.
> A tela de login avisa isso ao visitante.

## 3. Frontend na Vercel

1. **Add New → Project → GitHub** e importe o repositório `crm-frontend`. O Vite é detectado sozinho.

![Importar na Vercel](deploy/06-vercel-importar.png)

2. Em **Environment Variables**, defina:

| Variável | Valor |
|---|---|
| `VITE_API_URL` | `https://SEU-SERVICO.onrender.com/api/v1` |

3. **Deploy**:

![Deploy na Vercel](deploy/07-vercel-pronto.png)

4. Opcional: em **Settings → Domains**, troque o subdomínio gerado por um nome melhor.
   Escolha **"Redirect old domain to new"** para não perder links antigos.

![Domínio na Vercel](deploy/08-vercel-dominio.png)

## 4. Liberar o frontend na API (CORS)

No Render, abra o serviço → **Environment** → edite `CORS_ALLOWED_ORIGINS` com o endereço da Vercel,
**sem barra no final**. Vários endereços vão separados por vírgula:

```
https://crm-pro-jessica.vercel.app,https://crm-pro-seven-tan.vercel.app
```

Salve com **Save and deploy** e espere o novo deploy terminar:

![Novo deploy no Render](deploy/09-render-redeploy.png)

Pronto: abra o endereço da Vercel e clique em **Entrar como demo**.

---

## Atualizações

Depois do primeiro deploy, tudo é automático a cada merge na `main`:

- **GitHub Actions** roda os testes (backend com PostgreSQL real via Testcontainers; frontend com lint e build).
- **Render** e **Vercel** publicam a nova versão sozinhos.
- **Flyway** aplica as migrações novas do banco ao iniciar a API.

## Problemas comuns

| Sintoma | Causa provável |
|---|---|
| Login fica carregando e depois dá erro de conexão | `CORS_ALLOWED_ORIGINS` sem o endereço da Vercel, ou com `/` no final |
| Deploy da API falha logo ao iniciar | `DB_URL` sem `jdbc:` no começo, ou com usuário e senha dentro dela |
| `JWT_SECRET precisa ter pelo menos 32 caracteres` | segredo curto definido à mão; use 32+ caracteres aleatórios (o Render tem o botão **Generate**) |
| Página em branco ao recarregar `/funil` na Vercel | falta o `vercel.json` com o rewrite para `index.html` |
