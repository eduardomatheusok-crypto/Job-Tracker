-- Constraint único por usuário + empresa (case-insensitive) para evitar candidaturas duplicadas
-- Primeiro remove possíveis duplicatas existentes (mantém a mais recente)
DELETE FROM applications
WHERE id NOT IN (
    SELECT MAX(id)
    FROM applications
    GROUP BY user_id, LOWER(company_name)
);

CREATE UNIQUE INDEX idx_applications_user_company_unique
    ON applications(user_id, company_name);
