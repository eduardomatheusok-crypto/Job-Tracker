# Histórico de candidatura — foco exclusivo em mudanças de status

> Resumo de **onde, o que e como** foi feito para que o histórico registre e exiba
> somente a progressão de status (ex.: `Salva → Candidatada`), sem descrição dos
> campos atualizados.

## Objetivo

O histórico deve registrar **apenas mudanças de status** da candidatura:

```
Salva → Candidatada
Candidatada → Entrevista
Entrevista → Aprovada
```

Alterações em outros campos (empresa, cargo, localização, link da vaga, plataforma,
anotações) **não devem** gerar entrada de histórico nem exibir descrição
(ex.: `Updated fields: companyName, position, jobUrl, platform, status`).

## Decisões confirmadas

- **NÃO registrar a criação** da candidatura (deixa de existir a entrada `created`).
- **Só esconder a descrição no frontend**: o backend continua compondo o campo `note`,
  mas ele não é mais exibido.

## Onde / o que mudou

### 1. Backend — gravar somente quando o status muda
**Arquivo:** `src/main/java/jobtracker/service/ApplicationService.java`

- **Removida** a chamada `applicationHistoryService.recordCreation(...)` no `createApplication`
  (a criação não gera mais histórico).
- **Mudada a condição de gravação** no `updateApplication`: o histórico só é criado quando
  `savedApplication.getStatus() != previousStatus`. Antes, qualquer campo alterado
  (`!changedFields.isEmpty()`) gerava registro; agora, editar empresa/cargo/URL/plataforma/
  notas **sem** mudar o status não gera entrada.

**Como:** a variável `previousStatus` (lida antes de aplicar o request) já existe; basta
comparar com o status salvo.

### 2. Frontend — esconder a descrição
**Arquivo:** `frontend/src/pages/ApplicationDetailPage.tsx`, componente `HistoryCard`

- **Removida** a linha que renderizava `{item.note || '—'}`.
- O bloco que mostra `StatusBadge(previous) → StatusBadge(new)` foi **mantido**, pois é o que
  exibe a transição corretamente.

### 3. Testes atualizados
- **`src/test/java/jobtracker/JobtrackerIntegrationTests.java`**
  - Teste renomeado para `applicationHistoryShouldBeRecordedOnlyOnStatusChange`.
  - Criação → `0` registros; update de campos sem mudar status → `0` registros;
    mudança de status → `1` registro (com `previousStatus`/`newStatus` corretos).
- **`src/test/java/jobtracker/HistoryAndEmailIntegrationTests.java`**
  - `historyEndpoint_returnsStatusChangeRecordForOwnedApplication`: endpoint retorna
    apenas o registro de mudança de status.
- **`EmailParserServiceTest.java`**: sem alteração necessária (mocka `ApplicationService`).

## Como validar

```bash
# Backend (todos os testes)
mvnw test

# Frontend (typecheck)
cd frontend
node_modules/.bin/tsc -b
```

Resultado verificado: **39 testes do backend passando** e **typecheck do frontend OK**.

## Observações

- A **primeira transição** continua exibindo `Salva → Candidatada` mesmo sem registrar a
  criação, pois `previousStatus` vem do estado vivo da candidatura, não de um registro prévio.
- O **auto-sync do Gmail** (`EmailParserService`) altera status, então continua gerando
  histórico corretamente; notas enviadas junto não criam entrada extra (o que importa é a
  mudança de status).
- O campo `note`/`ApplicationHistoryResponse.note` continua existindo na API (back-end ainda
  o compõe), mas não é exibido no front. Se preferir removê-lo de vez no futuro, basta
  parar de compor o `note` em `ApplicationHistoryService.recordUpdate` e remover a linha do DTO.
- Não existe o status "Contratada/Entrevistada" no enum `ApplicationStatus` — a progressão
  usa os 6 status existentes (`Salva`, `Candidatada`, `Em processo`, `Entrevista`,
  `Rejeitada`, `Aprovada`).
