-- Constraint único por usuário + empresa (case-insensitive) para evitar candidaturas duplicadas
-- Primeiro remove possíveis duplicatas existentes (mantém a mais recente)
DELETE FROM applications a
USING applications b
WHERE a.user_id = b.user_id
  AND LOWER(a.company_name) = LOWER(b.company_name)
  AND a.id < b.id;

CREATE UNIQUE INDEX idx_applications_user_company_unique
    ON applications(user_id, LOWER(company_name));
