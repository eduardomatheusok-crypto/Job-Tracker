# JobTracker Capture — extensão atualizada

## Como a extensão funciona

Extensão **Manifest V3** (Vanilla JS) que lê a página da vaga aberta e envia os dados para a API do JobTracker.

Fluxo esperado:

1. O usuário abre uma página de vaga (LinkedIn, Gupy, Indeed, Greenhouse, etc.).
2. Abre o popup da extensão e faz login (a URL da API e o token JWT ficam no `chrome.storage.local`).
3. O popup pede ao `content.js` para **capturar os dados da página** automaticamente.
4. Os campos do formulário já **vêm preenchidos** (empresa, cargo, localização, link, plataforma).
5. O usuário **edita apenas o que estiver errado**, escolhe o status e salva.

### Como a captura funciona (ordem de prioridade)

- **JSON-LD (`JobPosting`)** — preferido; presente em Gupy, Workable, Greenhouse, etc.
- **Meta tags** (`og:title`, `og:site_name`) e **título/h1** da página.
- **Seletores específicos** para LinkedIn e Indeed.
- A **plataforma** é inferida a partir do domínio.

### Arquivos

| Arquivo | Papel |
|---|---|
| `manifest.json` | Declaração MV3 (permissões, service worker, content script) |
| `background.js` | Login, guarda token e faz `POST /api/applications` |
| `content.js` | Extrai os dados da página da vaga |
| `popup.html/css/js` | Interface: login + formulário de salvamento |
| `icons/` | Ícones |

### Instalação (modo desenvolvedor)

1. Abra `chrome://extensions` (ou `edge://extensions`).
2. Ative o **Modo do desenvolvedor**.
3. Clique em **Carregar sem compactação** e selecione a pasta `extension/`.

---

## O problema atual: campos aparecem para preenchimento manual

Quando o esperado era **reconhecer a página e puxar os dados sozinho**, na prática os campos chegam vazios e o usuário digita tudo. Causas principais:

1. **Falha silenciosa** — se a captura não retorna dados, o `popup.js` apenas não preenche nada, sem aviso ao usuário.
2. **Timing em SPA** — LinkedIn/Gupy/Indeed carregam a vaga via XHR **depois** do `document_idle`. Quando o popup pergunta, o DOM ainda não tem os dados → campos vazios.
3. **Content script nem sempre injetado** — em páginas restritas (`chrome://`, PDFs, etc.) não há fallback.

---

## 3 ideias para automatizar de verdade

### Ideia 1 — Captura resiliente (correção de raiz) ✅ implementada
- Adicionar **retry/polling** no popup: tentar capturar várias vezes em intervalos curtos até conseguir dados ou esgotar o tempo — cobre SPAs que carregam depois.
- **Fallback de injeção**: se o content script não responder, injetar a captura sob demanda via `chrome.scripting` (adicionar permissões `scripting` + `tabs`).
- Corrigir bug no parsing de JSON-LD e tratar páginas restritas com mensagem clara.
- **Resultado:** preenche sozinho na maioria dos casos e informa quando não consegue.

> **O que mudou no código:**
> - `manifest.json`: permissões `scripting` e `tabs` adicionadas.
> - `background.js`: no caso `capture`, tenta falar com o content script já injetado; se ele não responder, injeta `content.js` sob demanda (`chrome.scripting.executeScript`) e captura. Páginas restritas (`chrome://`, PDFs, etc.) retornam erro claro. Sem dados detectados, retorna `{ empty: true }`.
> - `popup.js`: `waitForCapture` faz retry/polling (6 tentativas a cada 400ms) para esperar SPAs carregarem; mostra mensagem de sucesso ao detectar ou aviso quando não conseguiu.
> - `content.js`: além de corrigir o bug no parsing de JSON-LD (`scripts.textContent` → `script.textContent`), corrigidos **bugs de raiz** que zeravam campos mesmo com dados presentes:
>   - Variável local `location` sombreava o objeto global `window.location`, quebrando `jobUrl` e `platform` (renomeada para `jobLocation` e passou a usar `window.location`).
>   - `textOf()` era aplicado em **strings simples** (título do JSON-LD e `og:site_name`), retornando vazio — removido o uso indevido.

### Ideia 2 — Feedback visual de captura (mata a percepção de "manual")
- Banner no topo do formulário: `✓ Dados detectados da página` ou `! Não foi possível detectar — preencha manualmente`.
- Destacar os campos auto-preenchidos para o usuário ver o que veio da página vs. o que está vazio.
- Botão **"Recapturar dados"** para tentar de novo sem fechar/reabrir o popup.
- **Resultado:** o usuário entende que a automação rodou e corrige apenas o que estiver errado.

### Ideia 3 — Extração mais rica e mais plataformas
- Usar **MutationObserver** no content script para capturar quando a vaga for carregada de forma assíncrona, em vez de capturar uma única vez.
- Adicionar mais seletores e campos extras (salário, senioridade, modalidade remoto/presencial/híbrido) se a API suportar.
- Ampliar a lista de domínios/plataformas já detectados.
- **Resultado:** maior cobertura e dados mais completos, menos edição manual.
