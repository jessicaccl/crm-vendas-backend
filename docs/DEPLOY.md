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

## 5. Sua empresa no mesmo deploy (multiempresa)

O CRM é multiempresa: a demo pública e os dados reais da sua empresa ficam **no mesmo serviço e no mesmo
banco**, separados por organização. Quem entra com o login demo nunca vê os dados da sua empresa.

No Render, abra o serviço → **Environment** → **Add variable** e defina:

| Variável | Valor |
|---|---|
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | seu login no CRM |
| `ADMIN_ORG_NAME` | nome da sua empresa, ex.: `Unic Studio` |
| `ADMIN_ORG_SLUG` | identificador curto, sem espaços nem acentos, ex.: `unic` (vai no endereço do webhook) |

Salve com **Save, rebuild, and deploy**. Na subida, a API cria a organização e coloca a sua conta nela;
se a conta já existia na Demo, ela é movida para a sua empresa.

## 6. Webhook no Netlify (leads do diagnóstico)

1. Entre no CRM com a **sua conta** e abra **Integrações**. Copie o **endereço do webhook**
   (termina com `/netlify/forms/unic`) e clique em **Gerar chave**. Copie a chave na hora: ela só é mostrada uma vez.
2. No Netlify, abra o site do quiz → **Site configuration → Notifications → Form submission notifications →
   Add notification → Outgoing webhook**:

   | Campo | Valor |
   |---|---|
   | Event to listen for | New form submission |
   | URL to notify | o endereço copiado no passo 1 |
   | Form | `leads-diagnostico` |
   | JWS secret token | a chave gerada no passo 1 |

3. Responda o quiz uma vez. O lead aparece em **Integrações** e no funil da sua empresa, em **Prospecto**.

Se a chave vazar, gere outra na mesma tela. A anterior deixa de funcionar na hora, e é só colar a nova no Netlify.

> **API dormindo:** no plano gratuito do Render a API dorme sem uso e leva até 1 minuto para acordar.
> Para não arriscar perder um webhook nesse intervalo, mantenha a API acordada com um monitor gratuito
> (ex.: [UptimeRobot](https://uptimerobot.com) ou [cron-job.org](https://cron-job.org)) chamando
> `https://SEU-SERVICO.onrender.com/actuator/health` a cada 10 minutos.
> De qualquer forma, todo envio continua guardado também no painel **Forms** do Netlify.

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
