ALTER TABLE investigator_repository
    ADD COLUMN git_url VARCHAR(1024) NULL,
    ADD COLUMN git_ref VARCHAR(200) NULL;
