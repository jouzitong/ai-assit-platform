-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: platform_ai_assist_ai_engine
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `platform_ai_assist_ai_engine`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `platform_ai_assist_ai_engine` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `platform_ai_assist_ai_engine`;

--
-- Table structure for table `ai_kb_document`
--

DROP TABLE IF EXISTS `ai_kb_document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_kb_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `kb_code` varchar(64) NOT NULL COMMENT '所属知识库编码',
  `document_code` varchar(128) NOT NULL COMMENT '文档编码，建议使用 sourceKey/tableName',
  `document_name` varchar(256) NOT NULL COMMENT '文档名称',
  `document_type` int NOT NULL COMMENT '文档类型枚举编码：1=DB_TABLE',
  `biz_type` int NOT NULL COMMENT '业务类型枚举编码：1=DB_DATA_SOURCE',
  `biz_key` varchar(128) NOT NULL COMMENT '业务唯一键',
  `source_system` varchar(64) DEFAULT NULL COMMENT '来源系统，例如 db-engine',
  `status` int NOT NULL DEFAULT '1' COMMENT '文档状态枚举编码：1=ACTIVE,2=DISABLED',
  `draft_version_no` int NOT NULL DEFAULT '1' COMMENT '当前草稿版本号',
  `content_checksum` char(64) DEFAULT NULL COMMENT '文档内容校验摘要，SHA-256',
  `content_format` int NOT NULL DEFAULT '1' COMMENT '内容格式枚举编码：1=MARKDOWN,2=TEXT,3=JSON',
  `content_size` bigint NOT NULL DEFAULT '0' COMMENT '文档内容大小，单位字节',
  `meta_json` mediumtext COMMENT '文档扩展元数据 JSON',
  `review_status` int NOT NULL DEFAULT '1' COMMENT '审核状态枚举编码：1=DRAFT,2=READY,3=REJECTED,4=PUBLISHED',
  `last_generated_at` datetime DEFAULT NULL COMMENT '最近一次生成时间',
  `last_error` varchar(1024) DEFAULT NULL COMMENT '最近一次错误信息',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint NOT NULL DEFAULT '-1' COMMENT '创建者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint NOT NULL DEFAULT '-1' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_document` (`kb_code`,`document_code`),
  KEY `idx_kb_document_biz` (`biz_type`,`biz_key`),
  KEY `idx_kb_document_status` (`kb_code`,`status`,`review_status`),
  KEY `idx_kb_document_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI知识库草稿文档表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_kb_document`
--

LOCK TABLES `ai_kb_document` WRITE;
/*!40000 ALTER TABLE `ai_kb_document` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_kb_document` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_kb_document_content`
--

DROP TABLE IF EXISTS `ai_kb_document_content`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_kb_document_content` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `document_id` bigint NOT NULL COMMENT '所属草稿文档 ID',
  `content_format` int NOT NULL DEFAULT '1' COMMENT '内容格式枚举编码：1=MARKDOWN,2=TEXT,3=JSON',
  `content_size` bigint NOT NULL DEFAULT '0' COMMENT '内容大小，单位字节',
  `content_json` mediumtext COMMENT '结构化内容 JSON',
  `rendered_content` mediumtext COMMENT '渲染后的最终文本内容',
  `ext_json` mediumtext COMMENT '正文扩展信息 JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint NOT NULL DEFAULT '-1' COMMENT '创建者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint NOT NULL DEFAULT '-1' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_content_document_id` (`document_id`),
  KEY `idx_document_content_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI知识库草稿文档正文表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_kb_document_content`
--

LOCK TABLES `ai_kb_document_content` WRITE;
/*!40000 ALTER TABLE `ai_kb_document_content` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_kb_document_content` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_kb_document_version`
--

DROP TABLE IF EXISTS `ai_kb_document_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_kb_document_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `kb_code` varchar(64) NOT NULL COMMENT '所属知识库编码',
  `document_code` varchar(128) NOT NULL COMMENT '文档编码',
  `kb_version_id` bigint NOT NULL COMMENT '所属知识库版本 ID',
  `version_no` int NOT NULL COMMENT '知识库版本号',
  `document_version_no` int NOT NULL COMMENT '文档自身版本号',
  `change_type` int NOT NULL COMMENT '变更类型枚举编码：1=CREATE,2=UPDATE,3=DELETE',
  `content_checksum` char(64) DEFAULT NULL COMMENT '文档内容校验摘要，SHA-256',
  `content_format` int NOT NULL DEFAULT '1' COMMENT '内容格式枚举编码：1=MARKDOWN,2=TEXT,3=JSON',
  `content_size` bigint NOT NULL DEFAULT '0' COMMENT '发布时文档内容大小，单位字节',
  `meta_json` mediumtext COMMENT '发布时扩展元数据快照 JSON',
  `source_system` varchar(64) DEFAULT NULL COMMENT '来源系统',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `published_by` varchar(64) DEFAULT NULL COMMENT '发布人',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint NOT NULL DEFAULT '-1' COMMENT '创建者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint NOT NULL DEFAULT '-1' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_document_version` (`kb_version_id`,`document_code`),
  KEY `idx_document_version_kb` (`kb_code`,`version_no`),
  KEY `idx_document_version_document` (`document_code`),
  KEY `idx_document_version_publish_time` (`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI知识库文档发布快照表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_kb_document_version`
--

LOCK TABLES `ai_kb_document_version` WRITE;
/*!40000 ALTER TABLE `ai_kb_document_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_kb_document_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_kb_document_version_content`
--

DROP TABLE IF EXISTS `ai_kb_document_version_content`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_kb_document_version_content` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `document_version_id` bigint NOT NULL COMMENT '所属文档版本快照 ID',
  `content_format` int NOT NULL DEFAULT '1' COMMENT '内容格式枚举编码：1=MARKDOWN,2=TEXT,3=JSON',
  `content_size` bigint NOT NULL DEFAULT '0' COMMENT '内容大小，单位字节',
  `content_json` mediumtext COMMENT '发布快照结构化内容 JSON',
  `rendered_content` mediumtext COMMENT '发布快照最终文本内容',
  `ext_json` mediumtext COMMENT '正文扩展信息 JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint NOT NULL DEFAULT '-1' COMMENT '创建者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint NOT NULL DEFAULT '-1' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_version_content_document_version_id` (`document_version_id`),
  KEY `idx_document_version_content_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI知识库文档发布快照正文表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_kb_document_version_content`
--

LOCK TABLES `ai_kb_document_version_content` WRITE;
/*!40000 ALTER TABLE `ai_kb_document_version_content` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_kb_document_version_content` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_kb_publish_task`
--

DROP TABLE IF EXISTS `ai_kb_publish_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_kb_publish_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_code` varchar(64) NOT NULL COMMENT '任务编码',
  `kb_code` varchar(64) NOT NULL COMMENT '所属知识库编码',
  `kb_version_id` bigint NOT NULL COMMENT '所属知识库版本 ID',
  `task_type` int NOT NULL COMMENT '任务类型枚举编码：1=PUBLISH,2=ROLLBACK',
  `status` int NOT NULL DEFAULT '1' COMMENT '任务状态枚举编码：1=PENDING,2=RUNNING,3=SUCCESS,4=FAILED,5=CANCELED',
  `progress_percent` int NOT NULL DEFAULT '0' COMMENT '当前进度百分比',
  `current_stage` varchar(64) DEFAULT NULL COMMENT '当前执行阶段，例如 PREPARE_VERSION',
  `request_json` mediumtext COMMENT '任务请求参数 JSON',
  `result_json` mediumtext COMMENT '任务执行结果 JSON',
  `error_message` varchar(2048) DEFAULT NULL COMMENT '失败错误信息',
  `started_at` datetime DEFAULT NULL COMMENT '启动时间',
  `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint NOT NULL DEFAULT '-1' COMMENT '创建者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint NOT NULL DEFAULT '-1' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_code` (`task_code`),
  KEY `idx_publish_task_kb` (`kb_code`,`status`),
  KEY `idx_publish_task_version` (`kb_version_id`),
  KEY `idx_publish_task_time` (`started_at`,`finished_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI知识库发布任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_kb_publish_task`
--

LOCK TABLES `ai_kb_publish_task` WRITE;
/*!40000 ALTER TABLE `ai_kb_publish_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_kb_publish_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_kb_store`
--

DROP TABLE IF EXISTS `ai_kb_store`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_kb_store` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `kb_code` varchar(64) NOT NULL COMMENT '本地知识库编码',
  `kb_name` varchar(128) NOT NULL COMMENT '知识库名称',
  `biz_type` int NOT NULL COMMENT '业务类型枚举编码：1=DB_DATA_SOURCE',
  `biz_key` varchar(128) NOT NULL COMMENT '业务唯一键，例如 sourceKey',
  `provider_code` varchar(64) DEFAULT NULL COMMENT 'Provider 编码，例如 qwen',
  `provider_kb_id` varchar(128) DEFAULT NULL COMMENT 'AI 侧真实知识库 ID',
  `current_version_id` bigint DEFAULT NULL COMMENT '当前生效版本 ID',
  `current_version_no` int DEFAULT NULL COMMENT '当前生效版本号',
  `status` int NOT NULL DEFAULT '1' COMMENT '知识库状态枚举编码：1=INIT,2=ACTIVE,3=SYNCING,4=FAILED,5=DISABLED',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `config_json` mediumtext COMMENT '知识库级可配置参数 JSON',
  `ext_json` mediumtext COMMENT '扩展信息 JSON',
  `last_publish_at` datetime DEFAULT NULL COMMENT '最近一次发布时间',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint NOT NULL DEFAULT '-1' COMMENT '创建者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint NOT NULL DEFAULT '-1' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_code` (`kb_code`),
  UNIQUE KEY `uk_kb_biz` (`biz_type`,`biz_key`),
  KEY `idx_kb_status_enabled` (`status`,`enabled`),
  KEY `idx_kb_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI知识库主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_kb_store`
--

LOCK TABLES `ai_kb_store` WRITE;
/*!40000 ALTER TABLE `ai_kb_store` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_kb_store` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_kb_version`
--

DROP TABLE IF EXISTS `ai_kb_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_kb_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `kb_code` varchar(64) NOT NULL COMMENT '所属知识库编码',
  `version_no` int NOT NULL COMMENT '知识库版本号',
  `version_name` varchar(128) NOT NULL COMMENT '版本名称',
  `status` int NOT NULL DEFAULT '1' COMMENT '版本状态枚举编码：1=DRAFT,2=CONFIRMED,3=PUBLISHING,4=PUBLISHED,5=FAILED,6=ROLLED_BACK',
  `publish_type` int DEFAULT NULL COMMENT '发布类型枚举编码：1=MANUAL,2=ROLLBACK',
  `source_snapshot_json` mediumtext COMMENT '本次发布选中的文档与来源快照 JSON',
  `summary_json` mediumtext COMMENT '版本摘要 JSON',
  `provider_sync_status` int NOT NULL DEFAULT '1' COMMENT 'AI 侧同步状态枚举编码：1=PENDING,2=RUNNING,3=SUCCESS,4=FAILED',
  `provider_sync_at` datetime DEFAULT NULL COMMENT 'AI 侧同步时间',
  `provider_sync_result_json` mediumtext COMMENT 'AI 侧同步结果回执 JSON',
  `draft_created_by` varchar(64) DEFAULT NULL COMMENT '草稿创建人标识',
  `published_by` varchar(64) DEFAULT NULL COMMENT '发布人',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `rollback_from_version_id` bigint DEFAULT NULL COMMENT '回滚来源版本 ID',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint NOT NULL DEFAULT '-1' COMMENT '创建者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint NOT NULL DEFAULT '-1' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_version` (`kb_code`,`version_no`),
  KEY `idx_kb_version_status` (`kb_code`,`status`),
  KEY `idx_kb_version_publish_time` (`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI知识库版本表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_kb_version`
--

LOCK TABLES `ai_kb_version` WRITE;
/*!40000 ALTER TABLE `ai_kb_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_kb_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'platform_ai_assist_ai_engine'
--

--
-- Dumping routines for database 'platform_ai_assist_ai_engine'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-18 20:29:04
