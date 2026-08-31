# Como verificar o funcionamento da extensão (JobTracker Capture)

Guia para testar a extensão do navegador de ponta a ponta, desde a instalação até a
confirmação de uma candidatura salva no backend.

## Pré-requisitos

- Backend no ar:
  ```powershell
  docker compose up -d --build   # em jobtracker/
  ```
  (ou `mvnw spring-boot:run`).
- Frontend rodando (para confirmar visualmente os registros):
  ```powershell
  cd frontend; npm run dev       # http://localhost:5173
  ```
- Uma conta de usuário criada no JobTracker (criar pelo frontend, página de registro).

## Nível 1 — Instalação

1. Abra `chrome://extensions` (ou `edge://extensions`).
2. Ative o **Modo do desenvolvedor**.
3. Clique em **Carregar sem compactação** e selecione a pasta `extension/`.
4. Clique no ícone da extensão na barra: o popup deve abrir mostrando a tela de login.

## Nível 2 — Login (testa a comunicação com o backend)

1. No popup, preencha:
   - **URL da API:** `http://localhost:8080`
   - **Email/senha:** conta registrada no JobTracker
2. Clique em entrar.
3. Resultado esperado: mensagem **"Conectado!"** e o e-mail exibido.

Isso valida o fluxo de autenticação JWT (o token fica salvo apenas no
`chrome.storage.local` da extensão).

## Nível 3 — Captura da vaga

1. Abra uma página de **detalhe da vaga** (ex.: Gupy, Greenhouse, Workable, LinkedIn).
2. Abra o popup.
3. Resultado esperado: os campos **posição, empresa, local, URL e plataforma** já vêm
   preenchidos a partir da página.
4. A captura tenta, em ordem: JSON-LD (`JobPosting`) → meta tags (`og:title`,
   `og:site_name`) → títulos/seletores específicos da página (LinkedIn, Indeed).

Os campos são apenas uma estimativa inicial — tudo é editável antes de salvar.

## Nível 4 — Salvamento

1. Escolha o **status** da candidatura.
2. Clique em **Salvar candidatura**.
3. Resultado esperado: **"Candidatura salva!"**.
4. Confirme no frontend (`http://localhost:5173`) → aba **Candidaturas**: a nova vaga
   deve aparecer na lista.

## Smoke test técnico (isola a API da extensão)

Para provar que o backend aceita o mesmo payload que a extensão envia:

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
  -ContentType application/json `
  -Body '{"email":"voce@email.com","password":"sua-senha"}'
$token = $login.token

Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/applications `
  -Headers @{Authorization = "Bearer $token"} `
  -ContentType application/json `
  -Body '{"companyName":"Acme","position":"Desenvolvedor","location":"Remoto","jobUrl":"https://exemplo.com/vaga","platform":"Teste"}'
```

Resposta **`201 Created`** com o objeto criado = contrato OK.

## Diagnóstico rápido

| Sintoma | Causa provável | Correção |
| --- | --- | --- |
| Login falha com erro de conexão | Backend fora do ar | Subir o backend e conferir `docker compose ps` |
| Login retorna 401/403 | Conta/senha inválida | Registrar usuário no frontend e repetir |
| "Faça login primeiro." ao salvar | Token ausente no armazenamento da extensão | Refazer o login no popup |
| "Sessão expirada." ao salvar | Token JWT expirado | Refazer o login no popup |
| Campos vazios na captura | Página de detalhe sem dados estruturados/seletores | Preencher os campos manualmente |
| Acesso negado (CORS) | `APP_CORS_ALLOWED_ORIGINS` restrito | Deixar vazio em dev (origem livre) ou liberar a origin da extensão |

## Arquivos relevantes

| Arquivo | Papel na verificação |
| --- | --- |
| `manifest.json` | Declara permissões/content script (`<all_urls>`) |
| `background.js` | Login (`POST /api/auth/login`) e salvamento (`POST /api/applications`) |
| `content.js` | Extração dos dados da página da vaga |
| `popup.html/css/js` | Formulário do popup (login + captura + status) |
| `src/.../ApplicationController.java` | Endpoint `POST /api/applications` consumido pela extensão |
| `src/.../ApplicationRequest.java` | Contrato do payload (`companyName`, `position` obrigatórios) |