-- Direcao do e-mail (recebido/enviado) e destinatario para e-mails enviados
ALTER TABLE emails ADD COLUMN direction VARCHAR(10) NOT NULL DEFAULT 'INBOUND';
ALTER TABLE emails ADD COLUMN to_address VARCHAR(255) NULL;

CREATE INDEX idx_emails_user_direction ON emails(user_id, direction);