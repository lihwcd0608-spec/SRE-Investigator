CREATE TABLE investigator_project (
    id BINARY(16) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    topology VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investigator_project_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_repository (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    repository_role VARCHAR(80) NOT NULL,
    root_path VARCHAR(1024) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investigator_repository_project_name (project_id, name),
    CONSTRAINT fk_investigator_repository_project
        FOREIGN KEY (project_id) REFERENCES investigator_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_tool_binding (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    tool_type VARCHAR(40) NOT NULL,
    profile VARCHAR(80) NOT NULL,
    encrypted_configuration LONGTEXT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investigator_binding (project_id, environment, tool_type, profile),
    CONSTRAINT fk_investigator_binding_project
        FOREIGN KEY (project_id) REFERENCES investigator_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_run (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    question TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    report LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_investigator_run_project_created (project_id, created_at),
    CONSTRAINT fk_investigator_run_project
        FOREIGN KEY (project_id) REFERENCES investigator_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_audit_event (
    id BINARY(16) NOT NULL,
    run_id BINARY(16) NULL,
    action VARCHAR(100) NOT NULL,
    target VARCHAR(512) NOT NULL,
    metadata LONGTEXT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_investigator_audit_run_time (run_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
