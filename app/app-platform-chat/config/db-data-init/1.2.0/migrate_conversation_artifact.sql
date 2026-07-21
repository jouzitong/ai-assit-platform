-- Conversation Artifact 精简结构的一次性迁移。
-- 执行前必须备份 conversation_artifact；仅 FILE、IMAGE、RENDER_JSON 可以进入新表结构。
-- 首个 SELECT 若返回记录，应先人工迁移/归档这些历史内容，再继续执行后续语句。

SELECT `id`, `artifact_code`, `artifact_type`
FROM `conversation_artifact`
WHERE UPPER(TRIM(`artifact_type`)) NOT IN ('FILE', 'IMAGE', 'RENDER_JSON')
   OR `round_code` IS NULL
   OR TRIM(`round_code`) = '';

-- 使用临时列转换，避免 MySQL 将未知字符串静默转换为 0。
ALTER TABLE `conversation_artifact`
ADD COLUMN `artifact_type_code` INT NULL COMMENT '产物类型迁移临时列';

UPDATE `conversation_artifact`
SET `artifact_type_code` = CASE
  WHEN `round_code` IS NULL OR TRIM(`round_code`) = '' THEN NULL
  ELSE CASE UPPER(TRIM(`artifact_type`))
    WHEN 'FILE' THEN 1
    WHEN 'IMAGE' THEN 2
    WHEN 'RENDER_JSON' THEN 3
    ELSE NULL
  END
END;

-- 存在不支持的历史类型时，此语句会失败，原 artifact_type 仍保留且未被覆盖。
ALTER TABLE `conversation_artifact`
MODIFY COLUMN `artifact_type_code` INT NOT NULL COMMENT '产物类型：1=文件,2=图片,3=Render JSON';

ALTER TABLE `conversation_artifact`
DROP COLUMN `session_code`,
DROP COLUMN `user_id`,
DROP COLUMN `related_message_code`,
DROP COLUMN `producer_type`,
DROP COLUMN `visible_flag`,
DROP COLUMN `status`,
DROP COLUMN `artifact_type`,
CHANGE COLUMN `artifact_type_code` `artifact_type` INT NOT NULL COMMENT '产物类型：1=文件,2=图片,3=Render JSON',
MODIFY COLUMN `round_code` VARCHAR(64) NOT NULL COMMENT '轮次编码',
MODIFY COLUMN `content_format` VARCHAR(32) NOT NULL DEFAULT 'JSON' COMMENT '内容格式',
MODIFY COLUMN `seq_no` INT NOT NULL DEFAULT 1 COMMENT '轮次内顺序',
ADD CONSTRAINT `conversation_artifact_chk_artifact_type` CHECK (`artifact_type` IN (1, 2, 3)),
ADD INDEX `conversation_artifact_idx_round_seq` (`round_code`, `seq_no`);
