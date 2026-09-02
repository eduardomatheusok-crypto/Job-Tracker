# Plano de Melhorias - JobTracker

## Problemas Identificados e Soluções

### 1. Performance do Login (3-10s)

**Causas Raiz:**
- **Cold Start do Render Free Tier**: Container desliga após 15min inativo, reinicialização leva 8-15s
- **BCrypt com custo 10**: Verificação de senha consome ~80-120ms
- **Duas queries no login**: `authenticationManager.authenticate()` + `userService.findByEmailOrThrow()` fazem queries duplicadas
- **HikariCP sem configuração**: Connection pool usa defaults, SSL handshake lento
- **JWT filter a cada request**: `loadUserByUsername()` executado em todas as requisições autenticadas

**Soluções:**
1. **Render Keep-Alive**: Configurar cron job (ex: cron-job.org) para pingar API a cada 10min
2. **BCrypt strength 8**: Alterar `PasswordEncoderConfig.java` para `new BCryptPasswordEncoder(8)`
3. **Unificar queries**: No `AuthService.login()`, usar apenas uma query (verificar senha manualmente)
4. **Configurar HikariCP**: Adicionar configs em `application.properties`
5. **Cache de UserDetails**: Implementar cache simples no `JwtAuthenticationFilter`

**Arquivos para modificar:**
- `PasswordEncoderConfig.java`
- `AuthService.java`
- `JwtAuthenticationFilter.java`
- `application.properties`

---

### 2. Candidaturas "Apagadas" Após Sincronização

**Causa Real:** Candidaturas **NÃO são apagadas** do banco. Problemas identificados:

1. **Matching frágil por nome**: `EmailParserService.java` usa `findAllByUserId()` + filtro em memória
2. **Candidaturas REJECTED não criadas**: Linha 101-114 do `EmailParserService.java`
3. **Possível confusão de UI**: "Desconectar Gmail" vs "Sair da conta"

**Soluções:**
1. **Melhorar query de matching**: Criar `findFirstByUserIdAndCompanyNameIgnoreCaseOrderByCreatedAtDesc()` no Repository
2. **Criar candidatura para REJECTED**: Modificar lógica no `EmailParserService`
3. **Constraint único**: Adicionar `(user_id, LOWER(company_name))` via migration
4. **Endpoint logout**: Criar `POST /api/auth/logout` no backend

**Arquivos para modificar:**
- `EmailParserService.java`
- `ApplicationRepository.java`
- Criar nova migration Flyway
- `AuthController.java`

---

### 3. Lupa de Pesquisa

**Solução:** Busca client-side na `ApplicationsPage.tsx`

**Implementação:**
- Input com ícone `Search` do lucide-react
- Filtrar por: companyName, position, platform, location
- Mensagem adaptada quando busca vazia
- Seguir padrão visual do `EmailsCard` em `ApplicationDetailPage.tsx`

**Arquivos para modificar:**
- `ApplicationsPage.tsx`

---

### 4. Emails Enviados Não Aparecem

**Causa Raiz:** `SEARCH_QUERY` em `GmailService.java` (linha 41-44) só busca emails com keywords no subject

**Solução:** Modificar query para incluir `label:sent`:
```java
private static final String SEARCH_QUERY =
    "(label:sent) OR (subject:(candidatura OR vaga OR \"processo seletivo\" OR entrevista OR \"application\" OR interview) "
        + "-from:(render.com OR vercel.com OR ...))";
```

**Arquivos para modificar:**
- `GmailService.java`

---

## Status da Implementação

### ✅ Concluído

1. **Performance Login**
   - ✅ Reduzir BCrypt strength de 10 para 8 (`PasswordEncoderConfig.java`)
   - ✅ Unificar queries no login (`AuthService.java` - agora usa apenas uma query + verificação manual de senha)
   - ✅ Configurar HikariCP (`application.properties` - max-pool 5, min-idle 1, timeouts)
   - ✅ Criar endpoint público de health-check `GET /api/health` + liberar na segurança

2. **Candidaturas**
   - ✅ Melhorar matching: `findFirstByUserIdAndCompanyNameIgnoreCaseOrderByCreatedAtDesc` (consulta no banco em vez de filtrar em memória)
   - ✅ Criar candidatura mesmo para status REJECTED
   - ✅ Endpoint de logout `POST /api/auth/logout` + chamada no frontend
   - ✅ Migration `V6__applications_unique_company.sql` (constraint único `(user_id, LOWER(company_name))` + limpeza de duplicatas)

3. **Emails Enviados**
   - ✅ SEARCH_QUERY dividida em `SEARCH_QUERY_INBOUND` + `SEARCH_QUERY_SENT` (`label:sent`), com deduplicação por messageId (`GmailService.java`)

4. **Lupa de Pesquisa**
   - ✅ Busca client-side na `ApplicationsPage.tsx` (filtra por empresa, cargo, plataforma, localização; segue padrão visual do EmailsCard)

### ✅ Verificação
- Backend: `mvnw compile` OK
- Frontend: `npm run build` OK, `npm run lint` OK

### 📋 Ação manual pendente (keep-alive para cold start)
O Render Free Tier desliga o container após ~15min de inatividade (cold start = 8-15s). A solução é um serviço externo que "acorda" a API periodicamente.

**Endpoint de health-check criado:** `GET /api/health` (público) → retorna `{"status":"UP","timestamp":...}`

**Como configurar (cron-job.org, grátis):**
1. Criar conta em https://cron-job.org
2. Novo agendamento (New Cron Job) apontando para:
   ```
   https://jobtracker-api.onrender.com/api/health
   ```
3. Intervalo: a cada **10 minutos** (mínimo permitido no plano gratuito)
4. Habilitar "Save failed executions"
5. Salvar

Alternativa que elimina o cold start de vez: upgrade para o **Render Starter** (~US$7/mês) em `render.yaml` (linha 10: `plan: free` → `plan: starter`).

---

## Notas Técnicas

- **Backend**: Java 21 + Spring Boot 4.1.1
- **Frontend**: React 19 + TypeScript 6 + Vite 8
- **Banco**: PostgreSQL (Supabase) com Flyway migrations
- **Deploy**: Render (backend) + Vercel (frontend)
- **Auth**: JWT stateless (24h expiração)
- **Gmail Integration**: OAuth2 + Gmail API