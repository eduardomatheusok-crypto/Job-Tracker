-- recebe a requisição e salva
ALTER TABLE gmail_connections ADD COLUMN encrypted_app_password VARCHAR(2000);
