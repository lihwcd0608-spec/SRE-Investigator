CREATE TABLE investigator_catalog_asset_file (
    id BINARY(16) NOT NULL,
    asset_id BINARY(16) NOT NULL,
    path VARCHAR(200) NOT NULL,
    content LONGTEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investigator_catalog_asset_file (asset_id, path),
    KEY idx_investigator_catalog_asset_file_asset (asset_id, sort_order),
    CONSTRAINT fk_investigator_catalog_asset_file_asset
        FOREIGN KEY (asset_id) REFERENCES investigator_catalog_asset (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
