# JobTracker Capture — Como funciona

Extensão **Manifest V3** em **Vanilla JS** que lê a página da vaga aberta no navegador, extrai os dados automaticamente e os envia para a API do JobTracker, eliminando o preenchimento manual de candidaturas.

## O que ela faz

Você visita a página de uma vaga (LinkedIn, Indeed, Gupy, Greenhouse, Workable, etc.), abre o popup da extensão e os campos já vêm preenchidos. Você só ajusta o que precisar, escolhe o status e salva — a candidatura vai direto para a sua conta no JobTracker.

## Fluxo de uso

1. Abra uma página de vaga no navegador.
2. Clique no ícone da extensão para abrir o popup.
3. Na primeira vez, informe a **URL da API** (ex.: `https://job-tracker-g732.onrender.com`) e seu **e-mail/senha** do JobTracker. O token JWT fica salvo apenas no `chrome.storage.local` da extensão.
4. O popup captura automaticamente os dados da vaga: **empresa, cargo, localização, link e plataforma**.
5. Revise os campos (tudo é editável), escolha o **status** e clique em **Salvar candidatura**.

## Como a captura funciona

A extração respeita uma ordem de prioridade:

- **JSON-LD (`JobPosting`)** — método preferido, presente em Gupy, Workable, Greenhouse e outras.
- **Meta tags** (`og:title`, `og:site_name`) e **título/h1** da página.
- **Seletores específicos** para LinkedIn e Indeed.
- A **plataforma** é inferida a partir do domínio da página.

A captura tenta aguardar o carregamento assíncrono das vagas em SPAs (retry/polling) e injeta o content script sob demanda se necessário. Ainda assim, é apenas uma estimativa inicial — todos os campos são editáveis antes de salvar.

## Instalação (modo desenvolvedor)

1. Abra `chrome://extensions` (ou `edge://extensions`).
2. Ative o **Modo do desenvolvedor**.
3. Clique em **Carregar sem compactação** e selecione a pasta `extension/` deste projeto.

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