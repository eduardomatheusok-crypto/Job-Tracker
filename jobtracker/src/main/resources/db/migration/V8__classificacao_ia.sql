-- Classificação de e-mails com LLM (Gemini)

-- E-mails já classificados: a sincronização pula os gmail_id presentes aqui.
-- tipo_email NULL = e-mail processado pelo parser antigo (regex) antes desta migração.
CREATE TABLE email_processado (
    id BIGSERIAL PRIMARY KEY,
    gmail_id VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    tipo_email VARCHAR(30),
    processado_em TIMESTAMP NOT NULL,
    CONSTRAINT fk_email_processado_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- E-mails já tratados pelo parser antigo não são reenviados ao Gemini
INSERT INTO email_processado (gmail_id, user_id, tipo_email, processado_em)
SELECT message_id, user_id, NULL, processed_at
FROM emails
WHERE processed_at IS NOT NULL
ON CONFLICT (gmail_id) DO NOTHING;

-- Anúncios de vaga recebidos por e-mail (não são candidaturas)
CREATE TABLE anuncio_vaga (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    email_id BIGINT,
    empresa VARCHAR(150),
    cargo VARCHAR(150),
    justificativa VARCHAR(2000),
    criado_em TIMESTAMP NOT NULL,
    CONSTRAINT fk_anuncio_vaga_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_anuncio_vaga_email FOREIGN KEY (email_id) REFERENCES emails (id) ON DELETE SET NULL
);

CREATE INDEX idx_anuncio_vaga_user ON anuncio_vaga(user_id);

-- Atualizações de status com confiança baixa, aguardando revisão do usuário
CREATE TABLE sugestao_status (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    email_id BIGINT,
    application_id BIGINT,
    empresa VARCHAR(150),
    cargo VARCHAR(150),
    novo_status VARCHAR(30),
    confianca DOUBLE PRECISION,
    justificativa VARCHAR(2000),
    situacao VARCHAR(20) NOT NULL,
    criado_em TIMESTAMP NOT NULL,
    CONSTRAINT fk_sugestao_status_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_sugestao_status_email FOREIGN KEY (email_id) REFERENCES emails (id) ON DELETE SET NULL,
    CONSTRAINT fk_sugestao_status_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE
);

CREATE INDEX idx_sugestao_status_user_situacao ON sugestao_status(user_id, situacao);

-- Origem de cada mudança no histórico (usuário ou agente de IA)
ALTER TABLE application_history ADD COLUMN origem VARCHAR(20) NOT NULL DEFAULT 'USUARIO';
