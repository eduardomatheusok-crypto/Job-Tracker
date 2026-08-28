# JobTracker

Sistema para armazenar, acompanhar e gerenciar candidaturas a vagas de emprego.

A ideia do projeto tem três pilares:

1. **API de candidaturas** — CRUD de candidaturas com histórico de mudanças de status, autenticação JWT e isolamento entre usuários.
2. **Extensão de navegador** — ao estar na página de uma vaga, a extensão captura os dados (empresa, cargo, link, plataforma) e envia para a API.
3. **Integração Gmail** — conexão OAuth para sincronizar e-mails de processos seletivos e atualizar o status das candidaturas automaticamente.

## Status atual

- [x] Autenticação (registro, login, JWT stateless)
- [x] CRUD de candidaturas com isolamento por usuário
- [x] Histórico de mudanças de status (persistido)
- [x] Perfil do usuário (`GET /api/users/me`)
- [x] Integração Gmail OAuth: conexão, sync manual de e-mails e parser automático
- [x] Sincronização automática (agendada) e resumo de resultados por status
- [x] Endpoints de histórico de candidatura e consulta de e-mails
- [x] Extensão de navegador (Manifest V3, Vanilla JS) — pasta `extension/`
- [x] Frontend (React + TypeScript) — pasta `frontend/`

## Stack

- Java 21
- Spring Boot 4.1.1
- Spring Security + JWT (jjwt 0.12.6)
- Spring Data JPA
- PostgreSQL 16
- Flyway
- Maven
- Google API Client (Gmail / OAuth)
- springdoc-openapi (Swagger UI em `/swagger-ui.html`)

## Estrutura

```
src/main/java/jobtracker
├── controller       # Endpoints REST + tratamento de erros
├── dto              # Entrada/saída da API (auth, user, application)
├── entity           # User, Application, ApplicationHistory, Email, GmailConnection
├── integration/gmail# OAuth, GmailService, EmailParserService, EncryptionService
├── repository       # Spring Data JPA
├── security         # SecurityConfig, JWT, filtro, UserDetails
└── service          # AuthService, UserService, ApplicationService, ApplicationHistoryService
```

## Como rodar

### Com Docker Compose

```bash
docker compose up --build
```

### Local (sem Docker)

Subir um PostgreSQL, preencher as variáveis de ambiente e rodar:

```bash
./mvnw spring-boot:run
```

### Variáveis de ambiente

| Variável | Obrigatória | Descrição |
|---|---|---|
| `DB_URL` | não | URL JDBC (default `jdbc:postgresql://localhost:5432/jobtracker`) |
| `DB_USERNAME` | não | Usuário do banco (default `postgres`) |
| `DB_PASSWORD` | não | Senha do banco |
| `JWT_SECRET` | sim | Chave do JWT, mínimo de 32 bytes |
| `JWT_EXPIRATION_MS` | não | Expiração do token (default `86400000`) |
| `GOOGLE_CLIENT_ID` | sim* | Client ID do app Google OAuth (*necessária para Gmail) |
| `GOOGLE_CLIENT_SECRET` | sim* | Client Secret do app Google OAuth (*necessária para Gmail) |
| `GOOGLE_REDIRECT_URI` | não | Redirect URI do OAuth (default `http://localhost:8080/api/gmail/callback`) |
| `ENCRYPTION_KEY` | sim | Chave de criptografia dos tokens OAuth (AES-GCM) |
| `CORS_ALLOWED_ORIGINS` | não | Origens permitidas no CORS (separadas por vírgula; vazio = libera tudo) |
| `GMAIL_SYNC_ENABLED` | não | Liga/desliga o sync agendado do Gmail (default `true`) |
| `GMAIL_SYNC_INITIAL_DELAY_MS` | não | Atraso inicial do job agendado (default `60000`) |
| `GMAIL_SYNC_FIXED_DELAY_MS` | não | Intervalo da sincronização automática (default `3600000`) |

Secrets não podem ficar no código nem em valores default de configuração — devem vir das variáveis de ambiente.

## Endpoints

### Autenticação (públicos)

- `POST /api/auth/register`
- `POST /api/auth/login`

### Candidaturas (JWT)

- `POST /api/applications`
- `GET /api/applications`
- `GET /api/applications/{id}`
- `GET /api/applications/{id}/history`
- `GET /api/applications/summary` — contagem de candidaturas por status (`total` + `byStatus`)
- `PUT /api/applications/{id}`
- `DELETE /api/applications/{id}`

### E-mails (JWT)

- `GET /api/emails` — e-mails sincronizados do usuário; opcional `?applicationId={id}`
- `GET /api/emails/{id}`

### Usuário (JWT)

- `GET /api/users/me`

### Gmail

- `GET /api/gmail/auth-url` (JWT)
- `GET /api/gmail/callback` (público — OAuth)
- `POST /api/gmail/sync` (JWT)
- `GET /api/gmail/status` (JWT)
- `POST /api/gmail/disconnect` (JWT)

## Status de candidatura

`SAVED`, `APPLIED`, `IN_PROCESS`, `INTERVIEW`, `REJECTED`, `ACCEPTED`.

Ao mudar para `APPLIED`, a data da candidatura (`appliedAt`) é automaticamente preenchida caso ainda esteja nula.

## Integração Gmail

O usuário autenticado solicita a URL de autorização (`/api/gmail/auth-url`). Após a permissão, o callback troca o código por tokens, resolve a conta Google concedida e persiste a conexão com os tokens criptografados (AES-GCM). O `POST /api/gmail/sync` busca e-mails de processos seletivos, persiste os não processados e o `EmailParserService` tenta:

- extrair empresa, cargo e status do e-mail;
- atualizar uma candidatura existente da mesma empresa;
- criar candidatura automaticamente quando não encontrada.

Há também uma sincronização automática agendada (`GmailSyncScheduler`) para todas as conexões ativas, com intervalo configurável via `GMAIL_SYNC_FIXED_DELAY_MS`. O parser opera fora do contexto HTTP (sem exigir token), usando o usuário dono do e-mail, e erros em um usuário não interrompem os demais.

**Nota:** os algoritmos de parsing são baseados em regras padrão. Alertas de rejeição/entrevista podem variar por remetente e exigem revisão humana.

## Documentação e Swagger

- Swagger UI: `/swagger-ui.html`
- Spec OpenAPI: `/v3/api-docs`

## Observações sobre os demais arquivos de documentação

- `README` (sem extensão): instruções do agente para desenvolvimento em etapas.
- `HELP.md` e `BASE`: contêm descrições históricas do projeto; podem estar desatualizadas em relação ao código (ex.: `APPROVED` vs `ACCEPTED`) e serão consolidados aqui.

## Extensão de navegador

Para instalar e usar, veja o [`extension/README.md`](extension/README.md).

A extensão (MV3, Vanilla JS) captura os dados da página da vaga — empresa, cargo, localização, link e plataforma — via JSON-LD, meta tags e seletores específicos (LinkedIn, Indeed, Gupy, Greenhouse, Workable...), exibe num popup para revisão e salva na API com o JWT do usuário.

## Frontend

A interface web fica em [`frontend/`](frontend/README.md) (Vite + React + TypeScript + Tailwind CSS + React Router).

Para rodar local:

```bash
cd frontend
cp .env.example .env   # ajuste VITE_API_URL (default http://localhost:8080)
npm install
npm run dev
```

Build de produção:

```bash
npm run build   # gera frontend/dist
```

## Próximos passos

1. Configurar o deploy: frontend na Vercel (pasta `frontend`, build `npm run build`, saída `dist`) e backend no Render (perfil `prod`, com `CORS_ALLOWED_ORIGINS` apontando para o domínio do frontend).