# JobTracker

Sistema para **armazenar, acompanhar e gerenciar candidaturas a vagas de emprego**.

O JobTracker centraliza todo o seu processo seletivo em três pilares:

1. **API de candidaturas** — CRUD de candidaturas com histórico de mudanças de status, autenticação JWT e isolamento entre usuários.
2. **Extensão de navegador** — ao estar na página de uma vaga, a extensão captura os dados (empresa, cargo, link, plataforma) e envia para a API.
3. **Integração Gmail** — conexão OAuth para sincronizar e-mails de processos seletivos e atualizar o status das candidaturas automaticamente.

---

## 🚀 Demonstração

> 👉 **Não leia só o código: [abra a demonstração e veja a experiência completa](https://job-tracker-vevw.vercel.app)**

Na demo você pode **criar uma conta gratuita**, adicionar candidaturas manualmente, acompanhar o histórico de status e testar a integração com a extensão. Acesse e experimente como o fluxo funciona de ponta a ponta.

> **Usando a extensão:** depois de criar sua conta na demo, copie o link da API `https://job-tracker-g732.onrender.com` e cole no campo **URL da API** do popup da extensão, junto com o seu e-mail e senha.

---

## ✨ Funcionalidades

- Autenticação com JWT (registro e login)
- CRUD de candidaturas com isolamento por usuário
- Status: `SAVED`, `APPLIED`, `IN_PROCESS`, `INTERVIEW`, `REJECTED`, `ACCEPTED`
- Histórico de mudanças de status (persistido)
- Resumo de candidaturas por status (`total` + `byStatus`)
- Integração Gmail via OAuth:
  - sincronização manual e automática (agendada)
  - parser automático de e-mails que cria/atualiza candidaturas
- Extensão de navegador (Manifest V3) que captura vagas de LinkedIn, Indeed, Gupy, Greenhouse, Workable e outras
- Frontend moderno em React + TypeScript + Tailwind CSS

## 🛠️ Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot, Spring Security + JWT (jjwt), Spring Data JPA |
| Banco | PostgreSQL 16 + Flyway |
| Gmail | Google API Client (OAuth 2.0) |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4 |
| Extensão | Manifest V3, Vanilla JS |
| Infra | Maven, Docker Compose, Vercel (frontend), Render (backend) |

## 📁 Estrutura

```
jobtracker
├── src/main/java/jobtracker
│   ├── controller        # Endpoints REST + tratamento de erros
│   ├── dto               # Entrada/saída da API
│   ├── entity            # User, Application, ApplicationHistory, Email, GmailConnection
│   ├── integration/gmail # OAuth, GmailService, EmailParserService, EncryptionService
│   ├── repository        # Spring Data JPA
│   ├── security          # SecurityConfig, JWT, filtro, UserDetails
│   └── service           # AuthService, UserService, ApplicationService, ...
├── frontend              # Interface web (Vite + React + TypeScript)
└── extension             # Extensão de navegador (MV3)
```

---

## ⬇️ Como baixar

Pré-requisitos:

- [Git](https://git-scm.com/)
- [JDK 21](https://adoptium.net/)
- [Node.js 20+](https://nodejs.org/)
- [Docker](https://www.docker.com/) (recomendado para o banco/backend)

```bash
git clone <URL_DO_REPOSITORIO>
cd jobtracker
```

---

## ⚙️ Preparando o ambiente

### 1. Backend (API)

Copie o arquivo de configuração e preencha com valores reais:

```bash
cp .env.example .env
```

Variáveis principais:

| Variável | Obrigatória | Descrição |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | sim | Credenciais do banco |
| `JWT_SECRET` | sim | Chave do JWT (mínimo 32 bytes) |
| `ENCRYPTION_KEY` | sim | Chave de criptografia dos tokens OAuth (AES-GCM) |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | para Gmail* | App Google OAuth |
| `GOOGLE_REDIRECT_URI` | não | Redirect do OAuth (default `http://localhost:8080/api/gmail/callback`; no Render: `https://job-tracker-g732.onrender.com/api/gmail/callback`) |
| `CORS_ALLOWED_ORIGINS` | não | Origens permitidas (separadas por vírgula; vazio = libera tudo) |
| `GMAIL_SYNC_*` | não | Ajustes da sincronização automática |

> \* Necessárias apenas para usar a integração com o Gmail. Sem elas o restante do sistema funciona normalmente.

Suba o banco e a API com Docker Compose:

```bash
docker compose up --build
```

**Sem Docker**, basta ter um PostgreSQL rodando e executar:

```bash
./mvnw spring-boot:run
```

A API fica em `http://localhost:8080` — Swagger UI disponível em `/swagger-ui.html`.
Em produção: `https://job-tracker-g732.onrender.com`.

### 2. Frontend

```bash
cd frontend
cp .env.example .env   # ajuste VITE_API_URL (local: http://localhost:8080 | produção: https://job-tracker-g732.onrender.com)
npm install
npm run dev
```

A interface fica em `http://localhost:5173`.

Para gerar o build de produção:

```bash
npm run build   # gera frontend/dist
```

---

## 🧩 Usando a extensão de navegador

A extensão captura automaticamente os dados da vaga aberta na página e os envia para a API.

### Instalação (modo desenvolvedor)

1. Abra `chrome://extensions` (ou `edge://extensions`).
2. Ative o **Modo do desenvolvedor**.
3. Clique em **Carregar sem compactação** e selecione a pasta `extension/` deste projeto.

### Como usar

1. Clique no ícone da extensão para abrir o popup.
2. Na primeira vez, informe a **URL da API** (ex.: `https://job-tracker-g732.onrender.com`) e seu **e-mail/senha** do JobTracker. O token JWT fica salvo apenas no `chrome.storage.local` da extensão.
3. Abra uma página de vaga (LinkedIn, Indeed, Gupy, Greenhouse, Workable...) e abra o popup: os campos já vêm preenchidos.
4. Ajuste o que precisar, escolha o **status** e clique em **Salvar candidatura**.

### Como a captura funciona

- Prefere dados estruturados **JSON-LD** (`JobPosting`), presentes em Gupy, Workable, Greenhouse e outras.
- Cai para **meta tags** (`og:title`, `og:site_name`) e **título/h1** da página.
- Tem seletores específicos para **LinkedIn** e **Indeed**.
- A **plataforma** é inferida a partir do domínio.

A captura é apenas uma estimativa inicial — todos os campos são editáveis antes de salvar.

---

## 🌐 Deploy

- **Frontend** → Vercel: importar a pasta `frontend`, build `npm run build`, saída `dist`; definir `VITE_API_URL=https://job-tracker-g732.onrender.com`.
- **Backend** → Render (ou similar): perfil `prod`, com `CORS_ALLOWED_ORIGINS` apontando para o domínio do frontend. Há um blueprint em `render.yaml`.

---
