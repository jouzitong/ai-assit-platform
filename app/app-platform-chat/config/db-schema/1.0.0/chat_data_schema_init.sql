-- app-platform-chat data schema init aggregation
-- Aggregated from data module structure initialization SQL only.
-- Excludes migrate/update scripts.

-- Source module: data-meta

CREATE TABLE IF NOT EXISTS ai_client_config
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    client_code VARCHAR(64)  NOT NULL COMMENT '客户端编码',
    client_name VARCHAR(128) NOT NULL COMMENT '客户端名称',
    client_type INT          NOT NULL COMMENT '对话客户端类型：1=SPRING_AI,2=AI_AGENT',
    base_url    VARCHAR(512)          DEFAULT NULL COMMENT '提供商请求基础地址',
    api_key     VARCHAR(2048)         DEFAULT NULL COMMENT 'API Key，当前直接明文存储',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '启用状态',
    ext_json    JSON                  DEFAULT NULL COMMENT '扩展配置JSON',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT       NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by  BIGINT       NOT NULL DEFAULT 0 COMMENT '更新者',
    version     BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_client_code (client_code),
    KEY idx_client_type (client_type)
) COMMENT ='AI客户端配置表';

CREATE TABLE IF NOT EXISTS ai_model_config
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    model_code  VARCHAR(64)  NOT NULL COMMENT '模型编码',
    model_name  VARCHAR(128) NOT NULL COMMENT '模型名称',
    client_id   BIGINT                DEFAULT NULL COMMENT 'AI客户端配置ID',
    client_type INT          NOT NULL COMMENT '对话客户端类型：1=SPRING_AI,2=AI_AGENT',
    base_url    VARCHAR(512)          DEFAULT NULL COMMENT '提供商请求基础地址',
    api_model   VARCHAR(128) NOT NULL COMMENT '提供商侧模型标识',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '启用状态：true启用，false禁用',
    api_key     VARCHAR(2048)         DEFAULT NULL COMMENT 'API Key，当前直接明文存储',
    ext_json    JSON                  DEFAULT NULL COMMENT '扩展配置JSON，例如 token 限额、温度参数等',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT       NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by  BIGINT       NOT NULL DEFAULT 0 COMMENT '更新者',
    version     BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记：0未删除，1已删除',
    UNIQUE KEY uk_model_code (model_code),
    UNIQUE KEY uk_client_api_model (client_id, api_model),
    KEY idx_client_id (client_id),
    KEY idx_client_type (client_type)
) COMMENT ='AI模型配置表';

-- Source module: data-kb

CREATE TABLE IF NOT EXISTS ai_kb_store
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    kb_code            VARCHAR(64)  NOT NULL COMMENT 'RAGFlow Dataset ID（本地知识库编码）',
    kb_name            VARCHAR(128) NOT NULL COMMENT '知识库名称',
    provider_kb_id     VARCHAR(128)          DEFAULT NULL COMMENT 'AI 侧真实知识库 ID',
    description        VARCHAR(512)          DEFAULT NULL COMMENT 'RAGFlow Dataset 描述',
    embedding_model    VARCHAR(256)          DEFAULT NULL COMMENT 'RAGFlow embedding 模型',
    permission         VARCHAR(32)           DEFAULT NULL COMMENT 'RAGFlow Dataset 权限',
    chunk_method       VARCHAR(64)           DEFAULT NULL COMMENT 'RAGFlow 分片方式',
    parser_config_json MEDIUMTEXT            DEFAULT NULL COMMENT 'RAGFlow parser_config JSON',
    parse_type         VARCHAR(64)           DEFAULT NULL COMMENT 'RAGFlow 自定义解析类型',
    pipeline_id        VARCHAR(128)          DEFAULT NULL COMMENT 'RAGFlow ingestion pipeline ID',
    enabled            TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用：1=启用，0=禁用',
    sync_status        INT          NOT NULL DEFAULT 2 COMMENT 'RAGFlow 同步状态：1=创建中,2=已同步,3=创建失败,4=更新中,5=更新失败,6=删除中,7=删除失败',
    sync_error         VARCHAR(1024)         DEFAULT NULL COMMENT '最近一次 RAGFlow 同步错误',
    last_sync_at       DATETIME              DEFAULT NULL COMMENT '最近一次 RAGFlow 同步时间',
    tags_json          MEDIUMTEXT            DEFAULT NULL COMMENT '知识库标签 JSON 数组',
    auth_json          MEDIUMTEXT            DEFAULT NULL COMMENT '知识库认证配置 JSON 快照',
    ext_json           MEDIUMTEXT            DEFAULT NULL COMMENT '扩展信息 JSON',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by         BIGINT       NOT NULL DEFAULT -1 COMMENT '创建者',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by         BIGINT       NOT NULL DEFAULT -1 COMMENT '更新者',
    version            BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    UNIQUE KEY uk_kb_code (kb_code),
    KEY idx_kb_enabled (enabled),
    KEY idx_kb_sync_status (sync_status),
    KEY idx_kb_update_time (update_time)
) COMMENT ='AI知识库主表';

CREATE TABLE IF NOT EXISTS ai_kb_document
(
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    kb_code              VARCHAR(64)  NOT NULL COMMENT '所属知识库编码',
    document_code        VARCHAR(128) NOT NULL COMMENT '文档编码，建议使用 sourceKey/tableName',
    document_name        VARCHAR(256) NOT NULL COMMENT '文档名称',
    document_type        INT          NOT NULL COMMENT '文档类型枚举编码：1=DB_TABLE',
    biz_type             INT          NOT NULL COMMENT '业务类型枚举编码：1=DB_DATA_SOURCE',
    biz_key              VARCHAR(128) NOT NULL COMMENT '业务唯一键',
    status               INT          NOT NULL DEFAULT 1 COMMENT '文档状态枚举编码：1=ACTIVE,2=DISABLED',
    provider_document_id VARCHAR(128)          DEFAULT NULL COMMENT 'AI 侧远端文档 ID',
    provider_sync_status INT          NOT NULL DEFAULT 1 COMMENT 'AI 侧同步状态枚举编码：1=PENDING,2=RUNNING,3=SUCCESS,4=FAILED',
    document_version_no  INT          NOT NULL DEFAULT 1 COMMENT '当前文档版本号',
    content_checksum     CHAR(64)              DEFAULT NULL COMMENT '文档内容校验摘要，SHA-256',
    content_format       INT          NOT NULL DEFAULT 1 COMMENT '内容格式枚举编码：1=MARKDOWN,2=TEXT,3=JSON',
    content_size         BIGINT       NOT NULL DEFAULT 0 COMMENT '文档内容大小，单位字节',
    meta_json            MEDIUMTEXT            DEFAULT NULL COMMENT '文档扩展元数据 JSON',
    last_generated_at    DATETIME              DEFAULT NULL COMMENT '最近一次生成时间',
    last_error           VARCHAR(1024)         DEFAULT NULL COMMENT '最近一次错误信息',
    remark               VARCHAR(512)          DEFAULT NULL COMMENT '备注',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by           BIGINT       NOT NULL DEFAULT -1 COMMENT '创建者',
    update_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by           BIGINT       NOT NULL DEFAULT -1 COMMENT '更新者',
    version              BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    UNIQUE KEY uk_kb_document (kb_code, document_code),
    KEY idx_kb_document_biz (biz_type, biz_key),
    KEY idx_kb_document_status (kb_code, status),
    KEY idx_kb_document_provider_sync (kb_code, provider_sync_status),
    KEY idx_kb_document_update_time (update_time)
) COMMENT ='AI知识库当前文档表';

CREATE TABLE IF NOT EXISTS ai_kb_document_content
(
    id               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    document_id      BIGINT   NOT NULL COMMENT '所属当前文档 ID',
    content_format   INT      NOT NULL DEFAULT 1 COMMENT '内容格式枚举编码：1=MARKDOWN,2=TEXT,3=JSON',
    content_size     BIGINT   NOT NULL DEFAULT 0 COMMENT '内容大小，单位字节',
    content_json     MEDIUMTEXT        DEFAULT NULL COMMENT '结构化内容 JSON',
    rendered_content MEDIUMTEXT        DEFAULT NULL COMMENT '渲染后的最终文本内容',
    ext_json         MEDIUMTEXT        DEFAULT NULL COMMENT '正文扩展信息 JSON',
    create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by       BIGINT   NOT NULL DEFAULT -1 COMMENT '创建者',
    update_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by       BIGINT   NOT NULL DEFAULT -1 COMMENT '更新者',
    version          BIGINT   NOT NULL DEFAULT 1 COMMENT '版本号',
    UNIQUE KEY uk_document_content_document_id (document_id),
    KEY idx_document_content_update_time (update_time)
) COMMENT ='AI知识库当前文档正文表';

CREATE TABLE IF NOT EXISTS ai_kb_document_version
(
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    kb_code             VARCHAR(64)  NOT NULL COMMENT '所属知识库编码',
    document_code       VARCHAR(128) NOT NULL COMMENT '文档编码',
    document_name       VARCHAR(256) NOT NULL COMMENT '文档名称',
    document_type       INT          NOT NULL COMMENT '文档类型枚举编码：1=DB_TABLE',
    biz_type            INT          NOT NULL COMMENT '业务类型枚举编码：1=DB_DATA_SOURCE',
    biz_key             VARCHAR(128) NOT NULL COMMENT '业务唯一键',
    document_version_no INT          NOT NULL COMMENT '文档自身版本号',
    change_type         INT          NOT NULL COMMENT '变更类型枚举编码：1=CREATE,2=UPDATE,3=DELETE',
    content_checksum    CHAR(64)              DEFAULT NULL COMMENT '文档内容校验摘要，SHA-256',
    content_format      INT          NOT NULL DEFAULT 1 COMMENT '内容格式枚举编码：1=MARKDOWN,2=TEXT,3=JSON',
    content_size        BIGINT       NOT NULL DEFAULT 0 COMMENT '发布时文档内容大小，单位字节',
    meta_json           MEDIUMTEXT            DEFAULT NULL COMMENT '发布时扩展元数据快照 JSON',
    snapshot_at         DATETIME              DEFAULT NULL COMMENT '快照时间',
    snapshot_by         VARCHAR(64)           DEFAULT NULL COMMENT '快照创建人',
    remark              VARCHAR(512)          DEFAULT NULL COMMENT '备注',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NOT NULL DEFAULT -1 COMMENT '创建者',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NOT NULL DEFAULT -1 COMMENT '更新者',
    version             BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    UNIQUE KEY uk_kb_document_version (kb_code, document_code, document_version_no),
    KEY idx_document_version_kb (kb_code, document_code),
    KEY idx_document_version_document (document_code),
    KEY idx_document_version_snapshot_time (snapshot_at)
) COMMENT ='AI知识库文档历史版本快照表';

CREATE TABLE IF NOT EXISTS ai_kb_document_version_content
(
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    document_version_id BIGINT   NOT NULL COMMENT '所属文档版本快照 ID',
    content_format      INT      NOT NULL DEFAULT 1 COMMENT '内容格式枚举编码：1=MARKDOWN,2=TEXT,3=JSON',
    content_size        BIGINT   NOT NULL DEFAULT 0 COMMENT '内容大小，单位字节',
    content_json        MEDIUMTEXT        DEFAULT NULL COMMENT '发布快照结构化内容 JSON',
    rendered_content    MEDIUMTEXT        DEFAULT NULL COMMENT '发布快照最终文本内容',
    ext_json            MEDIUMTEXT        DEFAULT NULL COMMENT '正文扩展信息 JSON',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT   NOT NULL DEFAULT -1 COMMENT '创建者',
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT   NOT NULL DEFAULT -1 COMMENT '更新者',
    version             BIGINT   NOT NULL DEFAULT 1 COMMENT '版本号',
    UNIQUE KEY uk_document_version_content_document_version_id (document_version_id),
    KEY idx_document_version_content_update_time (update_time)
) COMMENT ='AI知识库文档发布快照正文表';

CREATE TABLE IF NOT EXISTS ai_kb_publish_task
(
    id               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_code        VARCHAR(64) NOT NULL COMMENT '任务编码',
    kb_code          VARCHAR(64) NOT NULL COMMENT '所属知识库编码',
    task_type        INT         NOT NULL COMMENT '任务类型枚举编码：1=PUBLISH,2=ROLLBACK',
    status           INT         NOT NULL DEFAULT 1 COMMENT '任务状态枚举编码：1=PENDING,2=RUNNING,3=SUCCESS,4=FAILED,5=CANCELED',
    progress_percent INT         NOT NULL DEFAULT 0 COMMENT '当前进度百分比',
    current_stage    INT                  DEFAULT NULL COMMENT '当前执行阶段，例如 PREPARE_VERSION',
    request_json     MEDIUMTEXT           DEFAULT NULL COMMENT '任务请求参数 JSON',
    result_json      MEDIUMTEXT           DEFAULT NULL COMMENT '任务执行结果 JSON',
    error_message    VARCHAR(2048)        DEFAULT NULL COMMENT '失败错误信息',
    started_at       DATETIME             DEFAULT NULL COMMENT '启动时间',
    finished_at      DATETIME             DEFAULT NULL COMMENT '结束时间',
    create_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by       BIGINT      NOT NULL DEFAULT -1 COMMENT '创建者',
    update_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by       BIGINT      NOT NULL DEFAULT -1 COMMENT '更新者',
    version          BIGINT      NOT NULL DEFAULT 1 COMMENT '版本号',
    UNIQUE KEY uk_task_code (task_code),
    KEY idx_publish_task_kb (kb_code, status),
    KEY idx_publish_task_time (started_at, finished_at)
) COMMENT ='AI知识库发布任务表';

-- Source module: data-conversation

CREATE TABLE IF NOT EXISTS conversation_session
(
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    session_code  VARCHAR(64) NOT NULL COMMENT '会话编码',
    user_id       BIGINT      NOT NULL DEFAULT 0 COMMENT '用户ID',
    business_type INT                  DEFAULT NULL COMMENT '业务类型',
    session_name  VARCHAR(128)         DEFAULT NULL COMMENT '会话名称',
    pinned        TINYINT     NOT NULL DEFAULT 0 COMMENT '是否置顶',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by    BIGINT      NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by    BIGINT      NOT NULL DEFAULT 0 COMMENT '更新者',
    version       BIGINT      NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted       TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_session_code (session_code),
    KEY idx_user_id (user_id)
) COMMENT ='会话表';

CREATE TABLE IF NOT EXISTS conversation_round
(
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    round_code          VARCHAR(64) NOT NULL COMMENT '轮次编码',
    round_type          INT         NOT NULL DEFAULT 1 COMMENT '轮次类型',
    parent_round_code   VARCHAR(64)          DEFAULT NULL COMMENT '父轮次编码',
    session_code        VARCHAR(64) NOT NULL COMMENT '会话编码',
    user_id             BIGINT      NOT NULL DEFAULT 0 COMMENT '用户ID',
    model_code          VARCHAR(64)          DEFAULT NULL COMMENT '模型编码',
    actual_model        VARCHAR(128)         DEFAULT NULL COMMENT '实际调用模型',
    agent_run_id        VARCHAR(64)          DEFAULT NULL COMMENT 'Agent运行ID',
    root_agent_code     VARCHAR(64)          DEFAULT NULL COMMENT '根Agent编码',
    root_agent_version  INT                  DEFAULT NULL COMMENT '根Agent版本',
    agent_runtime_type  VARCHAR(32)          DEFAULT NULL COMMENT 'Agent运行时类型',
    agent_sdk_version   VARCHAR(64)          DEFAULT NULL COMMENT 'Agent SDK版本',
    agent_snapshot_hash VARCHAR(80)          DEFAULT NULL COMMENT 'Agent快照哈希',
    status              VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态',
    create_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by          BIGINT      NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by          BIGINT      NOT NULL DEFAULT 0 COMMENT '更新者',
    version             BIGINT      NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted             TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_round_code (round_code),
    KEY idx_session_code (session_code),
    KEY idx_user_id (user_id),
    KEY idx_round_agent_run (agent_run_id)
) COMMENT ='会话轮次表';

CREATE TABLE IF NOT EXISTS conversation_message
(
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    message_code        VARCHAR(64) NOT NULL COMMENT '消息编码',
    round_code          VARCHAR(64) NOT NULL COMMENT '轮次编码',
    session_code        VARCHAR(64) NOT NULL COMMENT '会话编码',
    role                VARCHAR(32) NOT NULL COMMENT '角色：USER/ASSISTANT',
    actor_type          VARCHAR(32) NOT NULL DEFAULT 'HUMAN' COMMENT '消息生产者类型',
    message_type        VARCHAR(32) NOT NULL DEFAULT 'USER_INPUT' COMMENT '消息业务类型',
    display_level       VARCHAR(32) NOT NULL DEFAULT 'VISIBLE' COMMENT '展示层级',
    content_format      VARCHAR(32) NOT NULL DEFAULT 'PLAIN_TEXT' COMMENT '内容格式',
    parent_message_code VARCHAR(64)          DEFAULT NULL COMMENT '父消息编码',
    source_message_code VARCHAR(64)          DEFAULT NULL COMMENT '源消息编码',
    status              VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '消息状态',
    content             MEDIUMTEXT  NOT NULL COMMENT '消息内容',
    sort_no             INT         NOT NULL DEFAULT 1 COMMENT '轮次内顺序',
    ext_json            MEDIUMTEXT           DEFAULT NULL COMMENT '扩展信息',
    create_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by          BIGINT      NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by          BIGINT      NOT NULL DEFAULT 0 COMMENT '更新者',
    version             BIGINT      NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted             TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_message_code (message_code),
    KEY idx_round_code (round_code),
    KEY idx_session_code (session_code)
) COMMENT ='会话消息表';

CREATE TABLE IF NOT EXISTS conversation_artifact
(
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    artifact_code        VARCHAR(64) NOT NULL COMMENT '产物编码',
    session_code         VARCHAR(64) NOT NULL COMMENT '会话编码',
    round_code           VARCHAR(64)          DEFAULT NULL COMMENT '轮次编码',
    user_id              BIGINT      NOT NULL DEFAULT 0 COMMENT '用户ID',
    related_message_code VARCHAR(64)          DEFAULT NULL COMMENT '关联消息编码',
    artifact_type        VARCHAR(32) NOT NULL COMMENT '产物类型',
    stage                VARCHAR(32) NOT NULL COMMENT '流程阶段',
    producer_type        VARCHAR(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '产物生产者',
    visible_flag         TINYINT     NOT NULL DEFAULT 0 COMMENT '是否对前端可见',
    title                VARCHAR(128)         DEFAULT NULL COMMENT '产物标题',
    content              MEDIUMTEXT  NOT NULL COMMENT '产物内容',
    content_format       VARCHAR(32) NOT NULL DEFAULT 'PLAIN_TEXT' COMMENT '内容格式',
    status               VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '产物状态',
    seq_no               INT         NOT NULL DEFAULT 1 COMMENT '会话内顺序',
    ext_json             MEDIUMTEXT           DEFAULT NULL COMMENT '扩展信息',
    create_time          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by           BIGINT      NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by           BIGINT      NOT NULL DEFAULT 0 COMMENT '更新者',
    version              BIGINT      NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted              TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_artifact_code (artifact_code),
    KEY idx_artifact_session_code (session_code),
    KEY idx_artifact_round_code (round_code),
    KEY idx_artifact_message_code (related_message_code)
) COMMENT ='会话过程产物表';

CREATE TABLE IF NOT EXISTS conversation_activity
(
    id               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    activity_code    VARCHAR(64)  NOT NULL COMMENT '活动事件编码',
    session_code     VARCHAR(64)  NOT NULL COMMENT '会话编码',
    round_code       VARCHAR(64)  NOT NULL COMMENT '轮次编码',
    user_id          BIGINT       NOT NULL DEFAULT 0 COMMENT '用户ID',
    agent_code       VARCHAR(64)           DEFAULT NULL COMMENT '执行 Agent 编码',
    correlation_code VARCHAR(128)          DEFAULT NULL COMMENT '同一活动生命周期关联编码',
    activity_type    VARCHAR(32)  NOT NULL COMMENT '活动类型',
    activity_name    VARCHAR(128) NOT NULL COMMENT '活动名称',
    source           VARCHAR(64)  NOT NULL COMMENT '活动来源',
    phase            VARCHAR(32)           DEFAULT NULL COMMENT '活动阶段',
    status           VARCHAR(32)  NOT NULL DEFAULT 'RUNNING' COMMENT '活动状态',
    message          VARCHAR(512)          DEFAULT NULL COMMENT '活动展示信息',
    input_summary    MEDIUMTEXT            DEFAULT NULL COMMENT '活动输入摘要',
    output_summary   MEDIUMTEXT            DEFAULT NULL COMMENT '活动输出摘要',
    duration_ms      BIGINT                DEFAULT NULL COMMENT '耗时毫秒',
    request_id       VARCHAR(128)          DEFAULT NULL COMMENT '请求追踪编码',
    seq_no           INT          NOT NULL DEFAULT 1 COMMENT '轮次内事件顺序',
    detail_json      MEDIUMTEXT            DEFAULT NULL COMMENT '活动结构化详情',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by       BIGINT       NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by       BIGINT       NOT NULL DEFAULT 0 COMMENT '更新者',
    version          BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    UNIQUE KEY uk_conversation_activity_code (activity_code),
    KEY idx_conversation_activity_session (session_code),
    KEY idx_conversation_activity_round_seq (round_code, seq_no),
    KEY idx_conversation_activity_correlation (round_code, correlation_code)
) COMMENT ='会话执行活动事件表';

-- Source module: data-agent-control

CREATE TABLE IF NOT EXISTS agent_definition
(
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    code            VARCHAR(64)  NOT NULL COMMENT 'Agent 编码',
    name            VARCHAR(128) NOT NULL COMMENT 'Agent 名称',
    description     VARCHAR(512)          DEFAULT NULL COMMENT 'Agent 说明',
    current_version INT                   DEFAULT NULL COMMENT '当前发布版本号',
    status          INT          NOT NULL COMMENT '目录状态：1=草稿,2=已校验,3=已发布,4=已归档',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '是否启用',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      BIGINT       NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by      BIGINT       NOT NULL DEFAULT 0 COMMENT '更新者',
    version         BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_agent_definition_code (code)
) COMMENT ='Agent 定义目录表';

CREATE TABLE IF NOT EXISTS agent_definition_version
(
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    agent_code      VARCHAR(64) NOT NULL COMMENT 'Agent 编码',
    version_no      INT         NOT NULL COMMENT '版本号',
    status          INT         NOT NULL COMMENT '版本状态：1=草稿,2=已校验,3=已发布,4=已归档',
    manifest_json   MEDIUMTEXT  NOT NULL COMMENT '完整中立 Agent Manifest JSON',
    validation_json MEDIUMTEXT           DEFAULT NULL COMMENT '最近一次校验报告 JSON',
    checksum        CHAR(64)    NOT NULL COMMENT 'Manifest SHA-256',
    published_at    DATETIME             DEFAULT NULL COMMENT '发布时间',
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      BIGINT      NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by      BIGINT      NOT NULL DEFAULT 0 COMMENT '更新者',
    version         BIGINT      NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted         TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除标记',
    KEY idx_agent_definition_version_agent (agent_code, version_no),
    KEY idx_agent_definition_version_status (status)
) COMMENT ='Agent 定义版本表';

CREATE TABLE IF NOT EXISTS agent_entry_binding
(
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    entry_code    VARCHAR(64) NOT NULL COMMENT '产品入口编码，例如 HOME_CHAT',
    agent_code    VARCHAR(64) NOT NULL COMMENT '绑定 Agent 编码',
    agent_version INT         NOT NULL COMMENT '绑定的已发布 Agent 版本',
    runtime_type  INT         NOT NULL COMMENT '运行时：1=OpenAI Agents Python,2=OpenAI Agents TypeScript',
    sdk_version   VARCHAR(64)          DEFAULT NULL COMMENT '目标 Agent SDK 版本约束',
    priority      INT         NOT NULL DEFAULT 100 COMMENT '同入口候选优先级，值越小优先',
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE COMMENT '是否启用',
    config_json   MEDIUMTEXT           DEFAULT NULL COMMENT '入口级非敏感运行配置 JSON',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by    BIGINT      NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by    BIGINT      NOT NULL DEFAULT 0 COMMENT '更新者',
    version       BIGINT      NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted       TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除标记',
    KEY idx_agent_entry_binding_entry (entry_code, enabled, priority),
    KEY idx_agent_entry_binding_agent (agent_code, agent_version)
) COMMENT ='Agent 入口绑定表';

CREATE TABLE IF NOT EXISTS agent_run_audit
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    run_id             VARCHAR(64) NOT NULL COMMENT 'Agent run id',
    session_code       VARCHAR(64)          DEFAULT NULL COMMENT '会话编码',
    round_code         VARCHAR(64)          DEFAULT NULL COMMENT '轮次编码',
    root_agent_code    VARCHAR(64) NOT NULL COMMENT '根 Agent 编码',
    root_agent_version INT         NOT NULL COMMENT '根 Agent 版本',
    workflow_code      VARCHAR(64)          DEFAULT NULL COMMENT 'Workflow 编码',
    workflow_version   INT                  DEFAULT NULL COMMENT 'Workflow 版本',
    runtime_type       INT         NOT NULL COMMENT '运行时类型',
    sdk_version        VARCHAR(64)          DEFAULT NULL COMMENT 'Agent SDK 版本',
    snapshot_hash      VARCHAR(80) NOT NULL COMMENT '冻结定义摘要（含算法前缀）',
    trace_id           VARCHAR(128)         DEFAULT NULL COMMENT 'Trace id',
    status             VARCHAR(32) NOT NULL COMMENT '运行状态',
    started_at         DATETIME             DEFAULT NULL COMMENT '开始时间',
    finished_at        DATETIME             DEFAULT NULL COMMENT '结束时间',
    usage_json         MEDIUMTEXT           DEFAULT NULL COMMENT '用量 JSON',
    error_summary      VARCHAR(1024)        DEFAULT NULL COMMENT '脱敏错误摘要',
    create_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by         BIGINT      NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by         BIGINT      NOT NULL DEFAULT 0 COMMENT '更新者',
    version            BIGINT      NOT NULL DEFAULT 1 COMMENT '版本号',
    UNIQUE KEY uk_agent_run_audit_run_id (run_id),
    KEY idx_agent_run_audit_session_round (session_code, round_code),
    KEY idx_agent_run_audit_root_agent (root_agent_code, root_agent_version),
    KEY idx_agent_run_audit_status_time (status, started_at)
) COMMENT ='Agent 运行审计表';

CREATE TABLE IF NOT EXISTS agent_skill
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    code        VARCHAR(64)  NOT NULL COMMENT 'Skill 编码',
    name        VARCHAR(128) NOT NULL COMMENT 'Skill 名称',
    `desc`      VARCHAR(1024)         DEFAULT NULL COMMENT 'Skill 简要说明',
    content     TEXT                  DEFAULT NULL COMMENT 'Skill Markdown 规则内容预览',
    tool_refs   JSON                  DEFAULT NULL COMMENT 'Skill 关联的 tool 编码列表',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '是否启用',
    remark      VARCHAR(1024)         DEFAULT NULL COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT       NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by  BIGINT       NOT NULL DEFAULT 0 COMMENT '更新者',
    version     BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_agent_skill_code (code)
) COMMENT ='Agent Skill 目录表';

CREATE TABLE IF NOT EXISTS agent_skill_version
(
    id               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    skill_code       VARCHAR(64)  NOT NULL COMMENT 'Skill 编码',
    version_no       INT          NOT NULL COMMENT '版本号',
    status           INT          NOT NULL COMMENT '版本状态：1=草稿,2=已校验,3=已发布,4=已归档',
    source_type      INT          NOT NULL COMMENT '来源：1=表单,2=ZIP',
    entrypoint       VARCHAR(512) NOT NULL COMMENT 'Skill 入口文件路径',
    manifest_json    MEDIUMTEXT   NOT NULL COMMENT 'Skill 包文件清单与兼容性 JSON',
    validation_json  MEDIUMTEXT            DEFAULT NULL COMMENT '最近一次校验报告 JSON',
    package_checksum CHAR(64)     NOT NULL COMMENT '规范化包内容 SHA-256',
    package_size     BIGINT       NOT NULL COMMENT '解压后总字节数',
    published_at     DATETIME              DEFAULT NULL COMMENT '发布时间',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by       BIGINT       NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by       BIGINT       NOT NULL DEFAULT 0 COMMENT '更新者',
    version          BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    KEY idx_agent_skill_version_skill (skill_code, version_no),
    KEY idx_agent_skill_version_status (status)
) COMMENT ='Agent Skill 版本表';

CREATE TABLE IF NOT EXISTS agent_skill_file
(
    id               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    skill_version_id BIGINT       NOT NULL COMMENT 'Skill 版本主键',
    path             VARCHAR(512) NOT NULL COMMENT '规范化包内相对路径',
    media_type       VARCHAR(128)          DEFAULT NULL COMMENT '推断的媒体类型',
    content_size     BIGINT       NOT NULL COMMENT '文件字节数',
    checksum         CHAR(64)     NOT NULL COMMENT '文件 SHA-256',
    content          LONGBLOB     NOT NULL COMMENT '文件内容',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by       BIGINT       NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by       BIGINT       NOT NULL DEFAULT 0 COMMENT '更新者',
    version          BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    KEY idx_agent_skill_file_version (skill_version_id),
    UNIQUE KEY uk_agent_skill_file_path (skill_version_id, path)
) COMMENT ='Agent Skill 文件表';

CREATE TABLE IF NOT EXISTS agent_skill_package
(
    id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    skill_version_id  BIGINT       NOT NULL COMMENT 'Skill 版本主键',
    original_filename VARCHAR(255) NOT NULL COMMENT '原始 ZIP 文件名',
    package_checksum  CHAR(64)     NOT NULL COMMENT '原始 ZIP SHA-256',
    compressed_size   BIGINT       NOT NULL COMMENT '压缩包字节数',
    content           LONGBLOB     NOT NULL COMMENT '原始 ZIP 内容',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by        BIGINT       NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by        BIGINT       NOT NULL DEFAULT 0 COMMENT '更新者',
    version           BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    KEY idx_agent_skill_package_version (skill_version_id),
    UNIQUE KEY uk_agent_skill_package_checksum (package_checksum)
) COMMENT ='Agent Skill 原始包表';

CREATE TABLE IF NOT EXISTS agent_tool
(
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    code         VARCHAR(64)  NOT NULL COMMENT 'Tool 编码',
    name         VARCHAR(128) NOT NULL COMMENT 'Tool 名称 / Agent 暴露名',
    `desc`       VARCHAR(1024)         DEFAULT NULL COMMENT 'Tool 说明',
    content      MEDIUMTEXT            DEFAULT NULL COMMENT '工具脚本内容',
    runtime_type VARCHAR(32)           DEFAULT NULL COMMENT '运行时类型，例如 PYTHON / JAVASCRIPT',
    sync_status  INT          NOT NULL DEFAULT 1 COMMENT '同步状态，例如 PENDING / SUCCESS / FAILED',
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '是否启用',
    remark       VARCHAR(1024)         DEFAULT NULL COMMENT '备注',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by   BIGINT       NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by   BIGINT       NOT NULL DEFAULT 0 COMMENT '更新者',
    version      BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_agent_tool_code (code)
) COMMENT ='Agent Tool 目录表';

CREATE TABLE IF NOT EXISTS agent_tool_version
(
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    tool_code       VARCHAR(64) NOT NULL COMMENT 'Tool 编码',
    version_no      INT         NOT NULL COMMENT '版本号',
    status          INT         NOT NULL COMMENT '版本状态：1=草稿,2=已校验,3=已发布,4=已归档',
    adapter_type    INT         NOT NULL COMMENT '适配器：1=FUNCTION,2=HTTP,3=MCP,4=SCRIPT',
    definition_json MEDIUMTEXT  NOT NULL COMMENT '输入输出 Schema、权限、超时和适配配置 JSON',
    validation_json MEDIUMTEXT           DEFAULT NULL COMMENT '最近一次校验报告 JSON',
    checksum        CHAR(64)    NOT NULL COMMENT '定义 SHA-256',
    published_at    DATETIME             DEFAULT NULL COMMENT '发布时间',
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      BIGINT      NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by      BIGINT      NOT NULL DEFAULT 0 COMMENT '更新者',
    version         BIGINT      NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted         TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除标记',
    KEY idx_agent_tool_version_tool (tool_code, version_no),
    KEY idx_agent_tool_version_status (status)
) COMMENT ='Agent Tool 版本表';

CREATE TABLE IF NOT EXISTS agent_workflow
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
    UNIQUE KEY uk_agent_workflow_code (code)
) COMMENT ='Agent Workflow 目录表';

CREATE TABLE IF NOT EXISTS agent_workflow_version
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    workflow_code      VARCHAR(64) NOT NULL COMMENT 'Workflow 编码',
    version_no         INT         NOT NULL COMMENT '版本号',
    status             INT         NOT NULL COMMENT '版本状态：1=草稿,2=已校验,3=已发布,4=已归档',
    specification_json MEDIUMTEXT  NOT NULL COMMENT '产出物、检查器和完成策略 JSON',
    validation_json    MEDIUMTEXT           DEFAULT NULL COMMENT '最近一次校验报告 JSON',
    checksum           CHAR(64)    NOT NULL COMMENT '规范 SHA-256',
    published_at       DATETIME             DEFAULT NULL COMMENT '发布时间',
    create_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by         BIGINT      NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by         BIGINT      NOT NULL DEFAULT 0 COMMENT '更新者',
    version            BIGINT      NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted            TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除标记',
    KEY idx_agent_workflow_version_workflow (workflow_code, version_no),
    KEY idx_agent_workflow_version_status (status)
) COMMENT ='Agent Workflow 版本表';
