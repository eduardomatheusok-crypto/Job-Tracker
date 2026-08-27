-- 1. Criação da tabela de usuários (users)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- 2. Criação da tabela de candidaturas (applications)
CREATE TABLE applications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    company_name VARCHAR(150) NOT NULL,
    position VARCHAR(150) NOT NULL,
    location VARCHAR(150),
    job_url VARCHAR(500),
    notes VARCHAR(2000),
    status VARCHAR(30) NOT NULL,
    applied_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_applications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 3. Criação da tabela de histórico de candidaturas (application_history)
CREATE TABLE application_history (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL,
    changed_by_user_id BIGINT,
    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    changed_field VARCHAR(100),
    note VARCHAR(2000),
    changed_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_history_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

-- 4. Criação da tabela de conexões com o Gmail (gmail_connections)
CREATE TABLE gmail_connections (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    gmail_address VARCHAR(255) NOT NULL,
    encrypted_access_token VARCHAR(2000),
    encrypted_refresh_token VARCHAR(2000),
    token_expires_at TIMESTAMP,
    connected_at TIMESTAMP NOT NULL,
    last_synced_at TIMESTAMP,
    active BOOLEAN NOT NULL,
    CONSTRAINT fk_gmail_connection_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 5. Criação da tabela de e-mails sincronizados (emails)
CREATE TABLE emails (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    application_id BIGINT,
    message_id VARCHAR(255) NOT NULL,
    subject VARCHAR(500),
    from_address VARCHAR(255),
    snippet VARCHAR(2000),
    raw_content VARCHAR(5000),
    received_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_emails_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_emails_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE SET NULL
);

-- Índices recomendados para otimização de buscas recorrentes
CREATE INDEX idx_applications_user ON applications(user_id);
CREATE INDEX idx_emails_message_user ON emails(message_id, user_id);
CREATE INDEX idx_history_application ON application_history(application_id);
