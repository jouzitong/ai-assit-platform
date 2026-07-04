CREATE TABLE IF NOT EXISTS ai_chat_workflow
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    code        VARCHAR(64)  NOT NULL COMMENT '流程编码',
    name        VARCHAR(128) NOT NULL COMMENT '流程名称',
    type        VARCHAR(32)  NOT NULL COMMENT '流程类型',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    config      MEDIUMTEXT            DEFAULT NULL COMMENT '流程目录配置JSON',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT       NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by  BIGINT       NOT NULL DEFAULT 0 COMMENT '更新者',
    version     BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_ai_chat_workflow_code (code)
) COMMENT ='AI流程目录表';

CREATE TABLE IF NOT EXISTS ai_chat_node
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    code        VARCHAR(64)  NOT NULL COMMENT '节点编码',
    name        VARCHAR(128) NOT NULL COMMENT '节点名称',
    type        VARCHAR(64)  NOT NULL COMMENT '节点类型',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    config      MEDIUMTEXT            DEFAULT NULL COMMENT '节点目录配置JSON',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT       NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by  BIGINT       NOT NULL DEFAULT 0 COMMENT '更新者',
    version     BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_ai_chat_node_code (code)
) COMMENT ='AI节点目录表';

CREATE TABLE IF NOT EXISTS ai_chat_skill
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    code        VARCHAR(64)  NOT NULL COMMENT 'Skill编码',
    name        VARCHAR(128) NOT NULL COMMENT 'Skill名称',
    type        VARCHAR(64)  NOT NULL COMMENT 'Skill类型',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    config      MEDIUMTEXT            DEFAULT NULL COMMENT 'Skill目录配置JSON',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT       NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by  BIGINT       NOT NULL DEFAULT 0 COMMENT '更新者',
    version     BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_ai_chat_skill_code (code)
) COMMENT ='AI Skill目录表';

CREATE TABLE IF NOT EXISTS ai_chat_workflow_config
(
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    code          VARCHAR(64)  NOT NULL COMMENT '配置编码',
    workflow_code VARCHAR(64)  NOT NULL COMMENT '流程编码',
    name          VARCHAR(128) NOT NULL COMMENT '配置名称',
    enabled       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    config        MEDIUMTEXT            DEFAULT NULL COMMENT '流程运行配置JSON',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by    BIGINT       NOT NULL DEFAULT 0 COMMENT '更新者',
    version       BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_ai_chat_workflow_config_code (code),
    KEY idx_ai_chat_workflow_config_workflow_code (workflow_code)
) COMMENT ='AI流程配置表';

CREATE TABLE IF NOT EXISTS ai_chat_workflow_config_node
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    config_code VARCHAR(64) NOT NULL COMMENT '流程配置编码',
    node_code   VARCHAR(64) NOT NULL COMMENT '节点编码',
    sort        INT         NOT NULL DEFAULT 1 COMMENT '节点顺序',
    next_code   VARCHAR(64)          DEFAULT NULL COMMENT '下一节点编码',
    enabled     TINYINT     NOT NULL DEFAULT 1 COMMENT '是否启用',
    config      MEDIUMTEXT           DEFAULT NULL COMMENT '节点运行配置JSON',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT      NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by  BIGINT      NOT NULL DEFAULT 0 COMMENT '更新者',
    version     BIGINT      NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted     TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_ai_chat_workflow_config_node (config_code, node_code),
    KEY idx_ai_chat_workflow_config_node_next_code (next_code)
) COMMENT ='AI流程配置节点表';

CREATE TABLE IF NOT EXISTS ai_chat_workflow_config_node_skill
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    config_code VARCHAR(64) NOT NULL COMMENT '流程配置编码',
    node_code   VARCHAR(64) NOT NULL COMMENT '节点编码',
    skill_code  VARCHAR(64) NOT NULL COMMENT 'Skill编码',
    phase       INT         NOT NULL COMMENT '挂接阶段',
    sort        INT         NOT NULL DEFAULT 1 COMMENT 'Skill顺序',
    enabled     TINYINT     NOT NULL DEFAULT 1 COMMENT '是否启用',
    config      MEDIUMTEXT           DEFAULT NULL COMMENT 'Skill挂接配置JSON',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT      NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by  BIGINT      NOT NULL DEFAULT 0 COMMENT '更新者',
    version     BIGINT      NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted     TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_ai_chat_workflow_config_node_skill (config_code, node_code, skill_code, phase),
    KEY idx_ai_chat_workflow_config_node_skill_skill_code (skill_code)
) COMMENT ='AI流程配置节点Skill表';

INSERT INTO ai_chat_workflow (code, name, type, enabled, config, created_by, updated_by)
VALUES ('ai-query-workflow', 'AI问数流程', 'QUERY', 1,
        '{"routeKey":"query","sceneDesc":"面向智能问数、SQL生成、指标分析和数据解释。","tags":["问数","SQL","规划"]}', 0,
        0),
       ('general-chat-workflow', '通用对话流程', 'CHAT', 1,
        '{"routeKey":"chat","sceneDesc":"面向普通问答、总结、改写和通用助手场景。","tags":["对话","总结","通用"]}', 0, 0),
       ('ai-app-workflow', 'AI应用流程', 'APP', 1,
        '{"routeKey":"app","sceneDesc":"面向带工具编排、业务节点和多步骤执行的应用流程。","tags":["应用","工具","编排"]}',
        0, 0),
       ('workflow-audit', '流程审查与回放', 'AUDIT', 1,
        '{"routeKey":"audit","sceneDesc":"面向流程版本核对、节点回放、问题追踪和治理审计。","tags":["审计","回放","治理"]}',
        0, 0)
ON DUPLICATE KEY UPDATE name       = VALUES(name),
                        type       = VALUES(type),
                        enabled    = VALUES(enabled),
                        config     = VALUES(config),
                        updated_by = VALUES(updated_by);

INSERT INTO ai_chat_node (code, name, type, enabled, config, created_by, updated_by)
VALUES ('chat-message', 'ChatMessageNode', 'ChatMessageNode', 1,
        '{"summary":"初始化会话、轮次和用户消息，为后续节点准备完整执行上下文。","executeMode":"SERIAL"}', 0, 0),
       ('query-planning', 'QueryPlanningNode', 'QueryPlanningNode', 1,
        '{"summary":"解析用户目标并生成结构化查询规划。","executeMode":"SERIAL"}', 0, 0),
       ('sql-generate', 'SqlGenerateNode', 'SqlGenerateNode', 1,
        '{"summary":"基于规划和知识上下文生成候选SQL。","executeMode":"SERIAL"}', 0, 0),
       ('render', 'RenderNode', 'RenderNode', 1, '{"summary":"汇总规划、知识上下文和SQL预生成结果生成最终回答。","executeMode":"SERIAL"}',
        0, 0)
ON DUPLICATE KEY UPDATE name       = VALUES(name),
                        type       = VALUES(type),
                        enabled    = VALUES(enabled),
                        config     = VALUES(config),
                        updated_by = VALUES(updated_by);

INSERT INTO ai_chat_skill (code, name, type, enabled, config, created_by, updated_by)
VALUES ('business_term_resolve', '术语解析', 'NODE_SKILL', 1,
        '{"summary":"抽取业务术语并回填给查询规划输入。","supportedPhases":["BEFORE_EXECUTE"]}', 0, 0),
       ('time_range_normalize', '时间范围归一化', 'NODE_SKILL', 1,
        '{"summary":"将自然语言时间转换成标准化时间范围。","supportedPhases":["BEFORE_EXECUTE"]}', 0, 0),
       ('query_plan_review', '查询规划审查', 'NODE_SKILL', 1,
        '{"summary":"对规划结果做结构和风险审查。","supportedPhases":["REVIEW_OUTPUT"]}', 0, 0),
       ('sql_generation_policy', 'SQL生成规范', 'NODE_SKILL', 1,
        '{"summary":"注入SQL硬约束、软规范和白名单规则。","supportedPhases":["BEFORE_EXECUTE"]}', 0, 0),
       ('user_preference_resolve', '用户偏好解析', 'NODE_SKILL', 1,
        '{"summary":"提取用户偏好，补充SQL生成软约束。","supportedPhases":["BEFORE_EXECUTE"]}', 0, 0)
ON DUPLICATE KEY UPDATE name       = VALUES(name),
                        type       = VALUES(type),
                        enabled    = VALUES(enabled),
                        config     = VALUES(config),
                        updated_by = VALUES(updated_by);

INSERT INTO ai_chat_workflow_config (code, workflow_code, name, enabled, config, created_by, updated_by)
VALUES ('ai-query-workflow-default', 'ai-query-workflow', 'AI问数默认配置', 1,
        '{"startNodeCode":"chat-message","options":{"scene":"query"}}', 0, 0)
ON DUPLICATE KEY UPDATE workflow_code = VALUES(workflow_code),
                        name          = VALUES(name),
                        enabled       = VALUES(enabled),
                        config        = VALUES(config),
                        updated_by    = VALUES(updated_by);

INSERT INTO ai_chat_workflow_config_node (config_code, node_code, sort, next_code, enabled, config, created_by,
                                          updated_by)
VALUES ('ai-query-workflow-default', 'chat-message', 1, 'query-planning', 1,
        '{"summary":"会话初始化","executeMode":"SERIAL","inputDefinitions":[{"fieldCode":"userMessage","fieldName":"用户问题","fieldPath":"request.message","dataType":"STRING","required":true,"sourceRef":"request","schema":{},"ext":{}}],"configItems":[{"code":"system-message","name":"系统提示消息","type":"PROMPT","summary":"定义节点初始化系统提示","enabled":true,"ext":{}}],"outputDefinitions":[{"fieldCode":"sessionContext","fieldName":"会话上下文","fieldPath":"context.session","dataType":"OBJECT","required":true,"sourceRef":"context","schema":{},"ext":{}}],"options":{},"ext":{}}',
        0, 0),
       ('ai-query-workflow-default', 'query-planning', 2, 'sql-generate', 1,
        '{"summary":"查询规划","executeMode":"SERIAL","inputDefinitions":[{"fieldCode":"currentQuestion","fieldName":"当前问题","fieldPath":"context.currentQuestion","dataType":"STRING","required":true,"sourceRef":"context","schema":{},"ext":{}}],"configItems":[{"code":"planning-prompt","name":"规划提示消息","type":"PROMPT","summary":"约束规划输出结构","enabled":true,"ext":{}}],"outputDefinitions":[{"fieldCode":"planResult","fieldName":"规划结果","fieldPath":"context.planResult","dataType":"OBJECT","required":true,"sourceRef":"context","schema":{},"ext":{}}],"options":{},"ext":{}}',
        0, 0),
       ('ai-query-workflow-default', 'sql-generate', 3, 'render', 1,
        '{"summary":"SQL生成","executeMode":"SERIAL","inputDefinitions":[{"fieldCode":"planResult","fieldName":"规划结果","fieldPath":"context.planResult","dataType":"OBJECT","required":true,"sourceRef":"context","schema":{},"ext":{}}],"configItems":[{"code":"sql-generate-prompt","name":"SQL生成提示","type":"PROMPT","summary":"定义SQL生成提示消息","enabled":true,"ext":{}}],"outputDefinitions":[{"fieldCode":"generatedSql","fieldName":"候选SQL","fieldPath":"context.generatedSql","dataType":"STRING","required":true,"sourceRef":"context","schema":{},"ext":{}}],"options":{},"ext":{}}',
        0, 0),
       ('ai-query-workflow-default', 'render', 4, NULL, 1,
        '{"summary":"结果渲染","executeMode":"SERIAL","inputDefinitions":[{"fieldCode":"generatedSql","fieldName":"候选SQL","fieldPath":"context.generatedSql","dataType":"STRING","required":false,"sourceRef":"context","schema":{},"ext":{}},{"fieldCode":"sqlPreGenerateResult","fieldName":"SQL预生成结果","fieldPath":"context.sqlPreGenerateResult","dataType":"OBJECT","required":false,"sourceRef":"context","schema":{},"ext":{}}],"configItems":[{"code":"render-template","name":"渲染模板","type":"PROMPT","summary":"定义最终回答渲染模板","enabled":true,"ext":{}}],"outputDefinitions":[{"fieldCode":"finalAnswer","fieldName":"最终回答","fieldPath":"context.finalAnswer","dataType":"STRING","required":true,"sourceRef":"context","schema":{},"ext":{}}],"options":{},"ext":{}}',
        0, 0)
ON DUPLICATE KEY UPDATE sort       = VALUES(sort),
                        next_code  = VALUES(next_code),
                        enabled    = VALUES(enabled),
                        config     = VALUES(config),
                        updated_by = VALUES(updated_by);

INSERT INTO ai_chat_workflow_config_node_skill (config_code, node_code, skill_code, phase, sort, enabled, config,
                                                created_by, updated_by)
VALUES ('ai-query-workflow-default', 'query-planning', 'business_term_resolve', 1, 1, 1,
        '{"required":false,"options":{},"ext":{}}', 0, 0),
       ('ai-query-workflow-default', 'query-planning', 'time_range_normalize', 1, 2, 1,
        '{"required":false,"options":{},"ext":{}}', 0, 0),
       ('ai-query-workflow-default', 'query-planning', 'query_plan_review', 3, 3, 1,
        '{"required":false,"options":{},"ext":{}}', 0, 0),
       ('ai-query-workflow-default', 'sql-generate', 'sql_generation_policy', 1, 1, 1,
        '{"required":false,"options":{},"ext":{}}', 0, 0),
       ('ai-query-workflow-default', 'sql-generate', 'user_preference_resolve', 1, 2, 1,
        '{"required":false,"options":{},"ext":{}}', 0, 0)
ON DUPLICATE KEY UPDATE sort       = VALUES(sort),
                        enabled    = VALUES(enabled),
                        config     = VALUES(config),
                        updated_by = VALUES(updated_by);
