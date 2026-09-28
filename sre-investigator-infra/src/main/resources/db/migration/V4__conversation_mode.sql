ALTER TABLE investigator_run
    ADD COLUMN conversation_mode VARCHAR(40) NOT NULL DEFAULT 'INVESTIGATE';
