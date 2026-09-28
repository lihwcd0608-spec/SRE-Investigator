ALTER TABLE investigator_conversation
    ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'CONVERSATION' AFTER conversation_mode;

UPDATE investigator_conversation c
INNER JOIN investigator_automation_execution e ON e.conversation_id = c.id
SET c.origin = 'AUTOMATION'
WHERE e.conversation_id IS NOT NULL;

UPDATE investigator_conversation c
INNER JOIN investigator_automation_job j ON j.conversation_id = c.id
SET c.origin = 'AUTOMATION'
WHERE j.conversation_id IS NOT NULL;

CREATE INDEX idx_investigator_conversation_project_origin_updated
    ON investigator_conversation (project_id, origin, updated_at);
