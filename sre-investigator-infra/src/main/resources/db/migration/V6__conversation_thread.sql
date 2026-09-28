CREATE TABLE investigator_conversation (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    conversation_mode VARCHAR(40) NOT NULL,
    title VARCHAR(512) NULL,
    memory_summary LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_investigator_conversation_project_updated (project_id, updated_at),
    CONSTRAINT fk_investigator_conversation_project
        FOREIGN KEY (project_id) REFERENCES investigator_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_message (
    id BINARY(16) NOT NULL,
    conversation_id BINARY(16) NOT NULL,
    message_role VARCHAR(20) NOT NULL,
    content LONGTEXT NOT NULL,
    run_id BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_investigator_message_conversation (conversation_id, created_at),
    CONSTRAINT fk_investigator_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES investigator_conversation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
