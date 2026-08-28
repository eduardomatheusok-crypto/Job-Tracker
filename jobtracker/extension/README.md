# JobTracker Capture (extensão de navegador)

Extensão **Manifest V3** em **Vanilla JS** que captura os dados da vaga aberta na página e envia para a API do JobTracker.

## Instalação (modo desenvolvedor)

1. Abra `chrome://extensions` (ou `edge://extensions`).
2. Ative o **Modo do desenvolvedor**.
3. Clique em **Carregar sem compactação** e selecione a pasta `extension/` deste projeto.

## Como usar

1. Clique no ícone da extensão (globalmente: é só abrir o popup).
2. Na primeira vez informe a **URL da API** (ex.: `http://localhost:8080`) e seu **e-mail/senha** do JobTracker. O token JWT fica salvo apenas no `chrome.storage.local` da extensão.
3. Abra uma página de vaga (LinkedIn, Indeed, Gupy, Greenhouse, Workable, etc.) e abra o popup: os campos já vêm preenchidos a partir da página.
4. Ajuste o que precisar, escolha o **status** e clique em **Salvar candidatura**.

## Como a captura funciona

- Prefere dados estruturados JSON-LD (`JobPosting`), presentes em Gupy, Workable, Greenhouse e outras.
- Cai para **meta tags** (`og:title`, `og:site_name`) e **título/h1** da página.
- Tem seletores específicos para **LinkedIn** e **Indeed**.
- A **plataforma** é inferida a partir do domínio (LinkedIn, Gupy, Indeed, ...).

A captura é apenas a estimativa inicial — os campos no popup são editáveis antes de salvar.

## Arquivos

| Arquivo | Papel |
|---|---|
| `manifest.json` | Declaração MV3 (permissões, service worker, content script) |
| `background.js` | Login na API, guarda o token e faz os `POST /api/applications` |
| `content.js` | Content script que extrai os dados da página da vaga |
| `popup.html/css/js` | Interface do popup (login + formulário de salvamento) |
| `icons/` | Ícones da extensão |

## Notas

- A URL da API e o token são guardados somente no armazenamento local da extensão.
- Exige que a API rode com CORS liberado (o `SecurityConfig` do backend já permite todas origens).
- Não intercepta nenhuma página; apenas lê dados quando o popup é aberto.