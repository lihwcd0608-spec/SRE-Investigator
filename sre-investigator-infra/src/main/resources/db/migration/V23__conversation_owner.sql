ALTER TABLE investigator_conversation
    ADD COLUMN created_by_user_id BINARY(16) NULL AFTER memory_summary;

ALTER TABLE investigator_conversation
    ADD KEY idx_investigator_conversation_owner (project_id, created_by_user_id, updated_at);
