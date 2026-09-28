UPDATE investigator_tool_binding
SET environment = 'test'
WHERE UPPER(environment) IN ('TEST', 'LOCAL');

UPDATE investigator_tool_binding
SET environment = 'pre'
WHERE UPPER(environment) IN ('PRE', 'GRAY');

UPDATE investigator_tool_binding
SET environment = 'prod'
WHERE UPPER(environment) IN ('PROD');

UPDATE investigator_run
SET environment = 'test'
WHERE UPPER(environment) IN ('TEST', 'LOCAL');

UPDATE investigator_run
SET environment = 'pre'
WHERE UPPER(environment) IN ('PRE', 'GRAY');

UPDATE investigator_run
SET environment = 'prod'
WHERE UPPER(environment) IN ('PROD');

UPDATE investigator_conversation
SET environment = 'test'
WHERE UPPER(environment) IN ('TEST', 'LOCAL');

UPDATE investigator_conversation
SET environment = 'pre'
WHERE UPPER(environment) IN ('PRE', 'GRAY');

UPDATE investigator_conversation
SET environment = 'prod'
WHERE UPPER(environment) IN ('PROD');

UPDATE investigator_automation_job
SET environment = 'test'
WHERE UPPER(environment) IN ('TEST', 'LOCAL');

UPDATE investigator_automation_job
SET environment = 'pre'
WHERE UPPER(environment) IN ('PRE', 'GRAY');

UPDATE investigator_automation_job
SET environment = 'prod'
WHERE UPPER(environment) IN ('PROD');
