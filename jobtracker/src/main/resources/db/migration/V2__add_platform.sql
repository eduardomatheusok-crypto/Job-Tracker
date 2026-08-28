ALTER TABLE applications ADD COLUMN platform VARCHAR(150) NULL;
CREATE INDEX idx_applications_platform ON applications(platform);