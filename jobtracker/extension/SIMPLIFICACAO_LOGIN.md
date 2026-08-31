# Proposta: simplificar/automatizar o login da extensão JobTracker Capture

## 1. Contexto

Hoje o popup da extensão exige **3 campos** para conectar: **Servidor (URL da API)**,
**E-mail** e **Senha**. Isso é repetitivo porque o usuário já autentica no site do
JobTracker com o mesmo e-mail/senha — e o token JWT usado pelo site já está no
`localStorage` da aba (`frontend/src/lib/api.ts` → chave `jobtracker.token`).

Sempre que o token expira, o usuário precisa redigitar tudo.

## 2. Objetivo

Eliminar a digitação de credenciais na extensão, reaproveitando a sessão já existente do
site JobTracker, mantendo a segurança atual (senha nunca persistida).

## 3. Abordagem recomendada: "Conectar com 1 clique"

### Comportamento novo

| Cenário | Comportamento do popup |
| --- | --- |
| Token salvo e **válido** | Abre direto no formulário de salvar (sem digitação) |
| Token salvo, porém **expirado** | Mostra aviso de sessão expirada + botão de reconectar (email já lembrado) |
| Aba do site aberta e logada | Botão principal **"Conectar com minha conta JobTracker"** captura o token da aba → entra sem senha |
| Nenhum dos casos acima | Formulário manual (atual), como fallback, com servidor/email pré-preenchidos |

### Como funciona a captura da sessão

1. O popup envia `connectFromSite` para o `background`.
2. O `background` encontra uma aba com o JobTracker aberto (ex.: `http://localhost:5173`).
3. Envia `jt:session` para o `content.js` da aba, que lê do `localStorage` do site:
   - `jobtracker.token` → token JWT
   - `jobtracker.user` → dados do usuário
4. O `background` valida o token com `GET {apiUrl}/api/users/me`.
5. Válido → grava no `chrome.storage.local` (mesmas chaves atuais) e abre o formulário.
6. Inválido → mensagem de erro/caminho manual.

### Servidor (URL da API)

- Padrão local: `http://localhost:8080` (mesmo default do site).
- Pode ser derivado da aba (ex.: aba em `localhost:5173` ⇒ API em `localhost:8080`).
- Mantido editável e lembrado no storage (necessário para deploy/backend público).

## 4. Segurança

- A **senha nunca é armazenada** — comportamento preservado.
- O token JWT já é o mecanismo de autenticação do app; transferir do site para o storage
  da extensão ocorre na própria máquina do usuário, na mesma sessão de navegador.
- Antes de "logar", o backend (`/api/users/me`) confirma que o token é válido.
- Em produção, o mesmo fluxo funciona lendo o token do site publicado (Vercel) — o
  usuário não precisa digitar nada na extensão.

## 5. Arquivos afetados

| Arquivo | Mudança |
| --- | --- |
| `extension/content.js` | Responder à mensagem `jt:session`, lendo `localStorage` do site JobTracker |
| `extension/background.js` | Mensagem `connectFromSite`; validação de sessão no `status` via `/api/users/me` |
| `extension/popup.js` | Lógica do botão de conexão automática; email/servidor lembrados; tratar sessão expirada |
| `extension/popup.html` | Botão "Conectar com minha conta JobTracker"; formulário manual recolhido |
| `extension/popup.css` | Estilos do novo fluxo |
| `extension/manifest.json` | **Sem alteração** (permissões atuais já cobrem `all_urls`) |

## 6. Alternativas avaliadas

### Opção mínima (S)
- Apenas memorizar e-mail e validar o token ao abrir o popup.
- Continua exigindo senha a cada nova sessão. Baixo esforço, baixo ganho.

### Opção máxima (A)
- Esconder até o campo "Servidor" (padrão fixo local).
- Quebra quando o backend é público/remoto, sem superfície de configuração.
- Não recomendada isoladamente.

### Por que a opção B é a escolhida
- Zero digitação no caso comum (site aberto/logado).
- Fallback manual preservado para deploy remoto e primeira conexão.
- Sem novas permissões e sem mudanças no backend.

## 7. Decisões em aberto

- Derivar o servidor automaticamente da aba ou manter campo editável pré-preenchido?
- Manter o formulário manual visível como link "Usar senha" (recomendado) ou em aba
  separada?
- Validar o token apenas ao abrir o popup ou a cada operação (salvar)?

## 8. Critérios de aceite

- [ ] Com o site logado aberto, 1 clique conecta o popup sem digitação.
- [ ] Token expirado → popup informa e oferece reconexão, sem travar.
- [ ] Sem aba do site → formulário manual funciona como hoje.
- [ ] Senha nunca gravada em storage.
- [ ] Fluxo de captura/salvamento inalterado após a conexão.