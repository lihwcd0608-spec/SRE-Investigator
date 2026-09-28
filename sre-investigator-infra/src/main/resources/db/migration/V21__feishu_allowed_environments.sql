-- 飞书群绑定允许的环境列表（逗号分隔）。environment 仍是未在话里指定时的默认环境。
ALTER TABLE investigator_feishu_chat_binding
    ADD COLUMN allowed_environments VARCHAR(64) NOT NULL DEFAULT '' AFTER environment;

UPDATE investigator_feishu_chat_binding
SET allowed_environments = environment
WHERE allowed_environments = '';
