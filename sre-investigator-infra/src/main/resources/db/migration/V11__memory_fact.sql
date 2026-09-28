CREATE TABLE investigator_memory_fact (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    kind VARCHAR(40) NOT NULL,
    content TEXT NOT NULL,
    source_conversation_id BINARY(16) NULL,
    qdrant_point_id BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_memory_fact_project_updated (project_id, updated_at),
    CONSTRAINT fk_memory_fact_project
        FOREIGN KEY (project_id) REFERENCES investigator_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
