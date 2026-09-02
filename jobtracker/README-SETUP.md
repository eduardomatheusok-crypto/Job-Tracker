# JobTracker — Guia de Setup (nova máquina)

Projeto com 3 módulos: **backend** (Java/Spring Boot, pasta `jobtracker/`), **frontend** (React/Vite, pasta `jobtracker/frontend/`) e **extensão Chrome** (pasta `jobtracker/extension/`).

Portas livres: **8080** (backend) e **5432** (postgres).


## 3. Backend

Crie o arquivo `jobtracker/.env` na raiz de `jobtracker/` com estes valores (já configurados e funcionando localmente):

```bash
# PostgreSQL
POSTGRES_DB=jobtracker
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
DB_PASSWORD=postgres

# JWT (>= 32 bytes)
JWT_SECRET=REDACTED
JWT_EXPIRATION_MS=86400000

# Criptografia local dos tokens OAuth
ENCRYPTION_KEY=REDACTED

# Google OAuth (integração Gmail)
GOOGLE_CLIENT_ID=997598166256-h4nhchrioags8gmsiln7ftctq36t6jd0.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=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
GOOGLE_REDIRECT_URI=http://localhost:8080/api/gmail/callback

# Sincronização automática do Gmail
GMAIL_SYNC_ENABLED=true
GMAIL_SYNC_INITIAL_DELAY_MS=60000
GMAIL_SYNC_FIXED_DELAY_MS=3600000

# CORS (vazio = libera tudo)
CORS_ALLOWED_ORIGINS=
```

**Banco de dados** — via Docker:

```bash
cd jobtracker
docker compose up db
```

Ou PostgreSQL local: suba um PG 16, usuário `postgres`, senha `postgres`, e crie o banco e o usuário com `CREATE DATABASE jobtracker;`. O servidor usa `jdbc:postgresql://localhost:5432/jobtracker`. As migrações Flyway (`src/main/resources/db/migration/`) criam o schema automaticamente no primeiro boot — não é necessário rodar SQL manual.

**Subir o backend (com o banco já no ar):**

```bash
./mvnw spring-boot:run   # Windows: mvnw.cmd spring-boot:run
```

Aplicação em `http://localhost:8080` (Swagger: `/swagger-ui.html`). Opção Docker integral: `docker compose up --build`.

## 4. Frontend

```bash
cd jobtracker/frontend
cp .env.example .env   # VITE_API_URL=http://localhost:8080
npm install
npm run dev
```

Aplicação em `http://localhost:5173`. Build: `npm run build`.

## 5. Extensão Chrome

Com o backend no ar: abra `chrome://extensions` → ative **Modo do desenvolvedor** → **Carregar sem compactação** → selecione a pasta `jobtracker/extension/`. No popup, informe a URL da API (`http://localhost:8080`) e o e-mail/senha registrado no sistema.

## 6. Pontos de atenção

- `.env` **não** vem do repositório (está no `.gitignore`) — recrie com os valores acima.
- Google OAuth é necessário **apenas** para a integração Gmail; o restante funciona sem ele.
- Rodar `docker-compose.yml` por inteiro (subir `db` + `app`) também funciona, mas pula o build manual.
- Testes do backend usam H2 em memória (`src/test/...`), sem banco externo.
- Para a extensão salvar candidaturas, o `SecurityConfig` já permite CORS para todas as origens em dev.