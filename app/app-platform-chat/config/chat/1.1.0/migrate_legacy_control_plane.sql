-- Optional one-time compatibility migration from 1.0 catalog rows.
-- Prerequisite: run create_table_ddl.sql first.
-- This script performs DML only. It never drops or mutates legacy tables.
-- All migrated versions are DRAFT and must pass the v1 validate/publish APIs before runtime use.

INSERT INTO `ai_chat_agent`
  (`code`, `name`, `description`, `current_version`, `status`, `enabled`, `deleted`, `version`)
SELECT n.`code`, n.`name`, n.`desc`, NULL, 1, COALESCE(n.`enabled`, TRUE), 0, 1
FROM `ai_chat_node` n
WHERE n.`deleted` = 0 AND n.`execute_type` = 2
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `description` = VALUES(`description`);

INSERT INTO `ai_chat_agent_version`
  (`agent_code`, `version_no`, `status`, `manifest_json`, `validation_json`, `checksum`, `deleted`, `version`)
SELECT migrated.`agent_code`, 1, 1, migrated.`manifest_json`,
       '{"valid":false,"errors":["Migrated Agent requires review and validation"],"warnings":[]}',
       SHA2(migrated.`manifest_json`, 256), 0, 1
FROM (
  SELECT n.`code` AS `agent_code`,
         CAST(JSON_OBJECT(
           'apiVersion', 'ai.platform/v1alpha1',
           'kind', 'Agent',
           'metadata', JSON_OBJECT(
             'code', n.`code`,
             'version', 1,
             'name', n.`name`,
             'description', n.`desc`,
             'labels', JSON_OBJECT('migration', 'legacy-node')
           ),
           'spec', JSON_OBJECT(
             'instructions', JSON_OBJECT(
               'type', 'inline',
               'text', CONCAT('Migrated from legacy node ', n.`code`, '. Review before publish.')
             ),
             'model', JSON_OBJECT(
               'ref', CASE
                 WHEN LOCATE('://', COALESCE(NULLIF(n.`model_code`, ''), 'default-quality')) > 0
                   THEN COALESCE(NULLIF(n.`model_code`, ''), 'model://default-quality')
                 ELSE CONCAT('model://', COALESCE(NULLIF(n.`model_code`, ''), 'default-quality'))
               END,
               'settings', JSON_OBJECT()
             ),
             'skillRefs', JSON_ARRAY(),
             'toolRefs', JSON_ARRAY(),
             'knowledgeRefs', JSON_ARRAY(),
             'mcpRefs', JSON_ARRAY(),
             'collaboration', JSON_OBJECT('agentTools', JSON_ARRAY(), 'handoffs', JSON_ARRAY()),
             'guardrails', JSON_OBJECT('input', JSON_ARRAY(), 'output', JSON_ARRAY()),
             'output', JSON_OBJECT('mode', 'text', 'workflowRef', NULL, 'schema', JSON_OBJECT()),
             'runtimeDefaults', JSON_OBJECT(
               'maxTurns', 8,
               'timeoutMs', 60000,
               'maxAgentDepth', 4,
               'toolConcurrency', 4,
               'stateStrategy', 'applicationReplay',
               'tracing', JSON_OBJECT('enabled', TRUE, 'includeSensitiveData', FALSE)
             ),
             'extensions', JSON_OBJECT('legacy', JSON_OBJECT(
               'nodeId', n.`id`,
               'skillRefs', n.`skill_refs`,
               'toolRefs', n.`tool_refs`,
               'knowledgeRefs', n.`kb_refs`,
               'inputConfig', n.`input_config`,
               'outputConfig', n.`output_config`
             ))
           )
         ) AS CHAR) AS `manifest_json`
  FROM `ai_chat_node` n
  WHERE n.`deleted` = 0 AND n.`execute_type` = 2
) migrated
ON DUPLICATE KEY UPDATE `agent_code` = VALUES(`agent_code`);

INSERT INTO `ai_chat_skill_version`
  (`skill_code`, `version_no`, `status`, `source_type`, `entrypoint`, `manifest_json`, `validation_json`,
   `package_checksum`, `package_size`, `deleted`, `version`)
SELECT s.`code`, 1, 1, 1, 'SKILL.md',
       CAST(JSON_OBJECT(
         'format', 'portable-agent-skill',
         'formatVersion', '1.0',
         'sourceType', 'FORM',
         'entrypoint', 'SKILL.md',
         'toolRefs', COALESCE(s.`tool_refs`, JSON_ARRAY()),
         'compatibleRuntimes', JSON_ARRAY(),
         'files', JSON_ARRAY(JSON_OBJECT(
           'path', 'SKILL.md',
           'size', OCTET_LENGTH(COALESCE(s.`content`, '')),
           'checksum', SHA2(COALESCE(s.`content`, ''), 256),
           'mediaType', 'text/markdown'
         ))
       ) AS CHAR),
       '{"valid":false,"errors":["Migrated Skill requires review and validation"],"warnings":[]}',
       SHA2(CONCAT('SKILL.md', CHAR(0), SHA2(COALESCE(s.`content`, ''), 256), CHAR(10)), 256),
       OCTET_LENGTH(COALESCE(s.`content`, '')), 0, 1
FROM `ai_chat_skill` s
WHERE s.`deleted` = 0
ON DUPLICATE KEY UPDATE `skill_code` = VALUES(`skill_code`);

INSERT INTO `ai_chat_skill_file`
  (`skill_version_id`, `path`, `media_type`, `content_size`, `checksum`, `content`, `deleted`, `version`)
SELECT v.`id`, 'SKILL.md', 'text/markdown', OCTET_LENGTH(COALESCE(s.`content`, '')),
       SHA2(COALESCE(s.`content`, ''), 256), CAST(COALESCE(s.`content`, '') AS BINARY), 0, 1
FROM `ai_chat_skill_version` v
JOIN `ai_chat_skill` s ON s.`code` = v.`skill_code` AND s.`deleted` = 0
WHERE v.`version_no` = 1 AND v.`source_type` = 1 AND v.`deleted` = 0
ON DUPLICATE KEY UPDATE `skill_version_id` = VALUES(`skill_version_id`);

INSERT INTO `ai_chat_tool_version`
  (`tool_code`, `version_no`, `status`, `adapter_type`, `definition_json`, `validation_json`,
   `checksum`, `deleted`, `version`)
SELECT migrated.`tool_code`, 1, 1, 4, migrated.`definition_json`,
       '{"valid":false,"errors":["Migrated Tool requires inputSchema, entrypoint and review"],"warnings":[]}',
       SHA2(migrated.`definition_json`, 256), 0, 1
FROM (
  SELECT t.`code` AS `tool_code`,
         CAST(JSON_OBJECT(
           'runtime', COALESCE(NULLIF(t.`runtime_type`, ''), 'UNKNOWN'),
           'entrypoint', CONCAT('legacy-inline:', t.`code`),
           'inputSchema', JSON_OBJECT(),
           'outputSchema', JSON_OBJECT(),
           'legacyInlineScript', t.`content`
         ) AS CHAR) AS `definition_json`
  FROM `ai_chat_tool` t
  WHERE t.`deleted` = 0
) migrated
ON DUPLICATE KEY UPDATE `tool_code` = VALUES(`tool_code`);

INSERT INTO `ai_chat_workflow_version`
  (`workflow_code`, `version_no`, `status`, `specification_json`, `validation_json`, `checksum`, `deleted`, `version`)
SELECT migrated.`workflow_code`, 1, 1, migrated.`specification_json`,
       '{"valid":false,"errors":["Migrated Workflow requires artifact deliverables"],"warnings":[]}',
       SHA2(migrated.`specification_json`, 256), 0, 1
FROM (
  SELECT w.`code` AS `workflow_code`,
         CAST(JSON_OBJECT(
           'apiVersion', 'ai.platform/v1alpha1',
           'kind', 'ArtifactWorkflow',
           'metadata', JSON_OBJECT(
             'code', w.`code`,
             'version', 1,
             'name', w.`name`,
             'description', CONCAT('Migrated from legacy Workflow ', w.`code`, '; define artifacts before publish.'),
             'labels', JSON_OBJECT('migration', 'legacy-workflow')
           ),
           'spec', JSON_OBJECT(
             'artifacts', JSON_ARRAY(),
             'checks', JSON_ARRAY(),
             'completionPolicy', JSON_OBJECT(
               'requireAllRequiredArtifacts', TRUE,
               'requireAllBlockingChecksPassed', TRUE
             ),
             'repairPolicy', JSON_OBJECT('maxRepairAttempts', 0, 'onExhausted', 'FAILED')
           )
         ) AS CHAR) AS `specification_json`
  FROM `ai_chat_workflow` w
  WHERE w.`deleted` = 0
) migrated
ON DUPLICATE KEY UPDATE `workflow_code` = VALUES(`workflow_code`);
