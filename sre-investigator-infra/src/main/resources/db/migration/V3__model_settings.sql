CREATE TABLE investigator_setting (
    setting_key VARCHAR(80) NOT NULL,
    encrypted_value LONGTEXT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
