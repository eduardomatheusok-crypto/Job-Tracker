-- raw_content ampliado para não truncar e-mails longos
ALTER TABLE emails ALTER COLUMN raw_content TYPE VARCHAR(20000);

-- Índice para a consulta de listagem de e-mails por usuário (ordenada por received_at)
CREATE INDEX idx_emails_user_received ON emails(user_id, received_at);