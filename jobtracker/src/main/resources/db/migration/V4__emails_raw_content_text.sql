-- raw_content como TEXT para não limitar e-mails longos
ALTER TABLE emails ALTER COLUMN raw_content TYPE TEXT;
