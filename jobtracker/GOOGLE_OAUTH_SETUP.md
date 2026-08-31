# Configuração do Google OAuth (Gmail) em modo de testes

Este documento explica como resolver o erro **`403: access_denied`** ("Analisador de
Candidaturas não concluiu o processo de verificação do Google") ao conectar o Gmail no
JobTracker.

## Contexto

O app usa o Google OAuth 2.0 para ler e-mails do Gmail (escopo `GMAIL_READONLY`).
Enquanto o aplicativo OAuth está **"In testing"** no Google Cloud, somente contas
adicionadas como **testadores aprovados** conseguem concluir a autorização. Qualquer outra
conta recebe a página de erro `403 access_denied` (mensagem de "testes").

Isso é uma configuração de **console do Google**, não um problema de código.

## Pré-requisitos

- Uma conta Google (a mesma usada para criar/possuir o *Client ID*).
- O projeto rodando localmente:
  - Backend em `http://localhost:8080` (Docker ou `mvnw spring-boot:run`).
  - Frontend em `http://localhost:5173` (`cd frontend && npm run dev`).
- Arquivos `.env` preenchidos (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`,
  `GOOGLE_REDIRECT_URI`).

## Passo a passo

### 1. Abra o Google Cloud Console

Acesse <https://console.cloud.google.com> logado com a conta dona do projeto.

### 2. Confirme que está no projeto certo

No final da página de erro 403, clique em **"detalhes do erro"** e localize o `client_id=...`
na seção *Request details*. Compare com o valor do arquivo `.env`:

```powershell
Get-Content .env | Select-String "GOOGLE_CLIENT_ID"
```

Os dois precisam ser iguais. Se o seletor de projeto no topo do console mostrar outro
projeto, troque o projeto.

### 3. Abra a tela de consentimento OAuth

Menu lateral → **APIs & Services** → **OAuth consent screen**.

### 4. Adicione o testador

- **User type** deve ser **External**. (Se for *Internal*, apenas contas do workspace da sua
  organização passam, ignorando a lista de testadores.)
- Role até a seção **Test users** → clique em **+ Add Users**.
- Adicione o e-mail da conta que usará para testar o Gmail.
- Salve (**Save**).

### 5. Confira o redirect URI

Menu → **APIs & Services** → **Credentials** → clique no seu *OAuth 2.0 Client ID*
(tipo **Web application**). Em **Authorized redirect URIs** deve existir exatamente:

```
http://localhost:8080/api/gmail/callback
```

Se faltar, adicione e salve. (Depois dessa alteração, os credenciais demoram alguns
minutos para propagar; o `redirect_uri_mismatch` indica divergência aqui.)

### 6. Habilite a Gmail API

Menu → **APIs & Services** → **Library** → pesquise **Gmail API** → **Enable**.

### 7. Teste

1. Aguarde alguns minutos para a propagação das alterações.
2. Abra uma **janela anônima** e faça login **somente** na conta adicionada como testadora.
3. No JobTracker (frontend), faça login e clique em conectar o Gmail.
4. O fluxo deve redirecionar para o Google, autorizar e voltar ao app com sucesso.

## Verificação do diretório de testes (seção *Test users*)

- O e-mail precisa ser adicionado na página *OAuth consent screen* do **mesmo projeto** que
  possui o `client_id` configurado no `.env`.
- Alterações de consentimento podem levar de minutos a algumas horas para valer em todos os
  clientes Google. Use a janela anônima para evitar sessão/cookies antigos.

## Troubleshooting

| Sintoma | Causa provável | Correção |
| --- | --- | --- |
| `403 access_denied` com mensagem de "modo de testes" | Conta não é testador, ou conta errada logada | Adicionar o e-mail em *Test users* e testar com essa conta (janela anônima) |
| `redirect_uri_mismatch` | URI do console ≠ `GOOGLE_REDIRECT_URI` | Ajustar *Authorized redirect URIs* para `http://localhost:8080/api/gmail/callback` |
| `403 access_denied` "Access Not Configured" | Gmail API desabilitada no projeto | Habilitar **Gmail API** (passo 6) |
| Falha apenas para contas externas | User type *Internal* | Trocar para *External* e adicionar testadores |
| Sempre 403 mesmo após configurar | `client_id` do `.env` é de outro projeto | Passo 2 — alinhar projeto/credenciais |

## Notas de produção

- Ao publicar o aplicativo (tirar do modo de testes), o `GOOGLE_REDIRECT_URI` deve apontar
  para a **URL pública do backend**, e essa mesma URL precisa estar em
  *Authorized redirect URIs* no console.
- O escopo Gmail é classificado pelo Google como **sensível**: em produção, apps não
  verificados podem continuar sendo bloqueados até concluir a verificação do OAuth.