ALTER TABLE investigator_tool_binding
    ADD COLUMN summary VARCHAR(280) NULL,
    ADD COLUMN tags VARCHAR(400) NULL;

ALTER TABLE investigator_catalog_asset
    ADD COLUMN summary VARCHAR(280) NULL,
    ADD COLUMN tags VARCHAR(400) NULL;
