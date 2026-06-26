-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: platform_ai_assist_ai_chat
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
-- Current Database: `platform_ai_assist_ai_chat`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `platform_ai_assist_ai_chat` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `platform_ai_assist_ai_chat`;

--
-- Table structure for table `ai_chat_artifact`
--

DROP TABLE IF EXISTS `ai_chat_artifact`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_artifact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `artifact_code` varchar(64) NOT NULL COMMENT '产物编码',
  `session_code` varchar(64) NOT NULL COMMENT '会话编码',
  `round_code` varchar(64) DEFAULT NULL COMMENT '轮次编码',
  `user_id` bigint NOT NULL DEFAULT '0' COMMENT '用户ID',
  `related_message_code` varchar(64) DEFAULT NULL COMMENT '关联消息编码',
  `artifact_type` varchar(32) NOT NULL COMMENT '产物类型',
  `stage` varchar(32) NOT NULL COMMENT '流程阶段',
  `producer_type` varchar(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '产物生产者',
  `visible_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否对前端可见',
  `title` varchar(128) DEFAULT NULL COMMENT '产物标题',
  `content` mediumtext NOT NULL COMMENT '产物内容',
  `content_format` varchar(32) NOT NULL DEFAULT 'PLAIN_TEXT' COMMENT '内容格式',
  `status` varchar(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '产物状态',
  `seq_no` int NOT NULL DEFAULT '1' COMMENT '会话内顺序',
  `ext_json` mediumtext COMMENT '扩展信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_artifact_code` (`artifact_code`),
  KEY `idx_artifact_session_code` (`session_code`),
  KEY `idx_artifact_round_code` (`round_code`),
  KEY `idx_artifact_message_code` (`related_message_code`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI聊天过程产物表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_artifact`
--

LOCK TABLES `ai_chat_artifact` WRITE;
/*!40000 ALTER TABLE `ai_chat_artifact` DISABLE KEYS */;
INSERT INTO `ai_chat_artifact` VALUES (1,'artifact-59278c107ab44e13a80482eb84f741b2','session-e755e13d5a814d9fa248aac02eb0c1ef','round-11e0431438704892a885886f90b27c1d',0,'msg-a3aca59484a3497294c46f6eaf822808','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Feign call failed, methodKey=AiMetaQueryApi#listModels(AiMetaQueryRequest), status=404, body=<!doctype html><html lang=\"en\"><head><title>HTTP Status 404 – Not Found</title><style type=\"text/css\">body {font-family:Tahoma,Arial,sans-serif;} h1, h2, h3, b {color:white;background-color:#525D76;} h1 {font-size:22px;} h2 {font-size:16px;} h3 {font-size:14px;} p {font-size:12px;} a {color:black;} .line {height:1px;background-color:#525D76;border:none;}</style></head><body><h1>HTTP Status 404 – Not Found</h1></body></html>','PLAIN_TEXT','FAILED',1,NULL,'2026-06-10 21:26:53','2026-06-10 21:26:53',0,-1,1,0),(2,'artifact-200b8e8e46f74c5082a5c6438696dccf','session-d8e776e5209145d9a6ba47c30b87de85','round-dc7df352459045bd8eb4f17c8fe4219e',0,'msg-1ed2bfc63d58433c8fd8163afc6ac802','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Error while extracting response for type [java.util.List<ai.platform.aiassist.service.ai.api.dto.AiModelConfigDTO>] and content type [application/json]','PLAIN_TEXT','FAILED',1,NULL,'2026-06-10 21:32:26','2026-06-10 21:32:26',0,-1,1,0),(3,'artifact-e33c65451cfb498d9e7fbaba4bf0ae9a','session-e755e13d5a814d9fa248aac02eb0c1ef','round-be17f202108345deaed7117e0b59b1a9',0,'msg-e6d6c150830544699c727bb5a6c34c30','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Error while extracting response for type [java.util.List<ai.platform.aiassist.service.ai.api.dto.AiModelConfigDTO>] and content type [application/json]','PLAIN_TEXT','FAILED',1,NULL,'2026-06-10 22:03:02','2026-06-10 22:03:02',1,-1,1,0),(4,'artifact-39c4ed9624de4a74b6f74ce55ef50ddc','session-e755e13d5a814d9fa248aac02eb0c1ef','round-fcef2a5d703848f8801e8fd319efc2ea',0,'msg-d55335f7a412467ba3da405756f6551d','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Read timed out executing POST http://app-platform-ai-engine/aiEngine/api/v1/ai/execution/chat','PLAIN_TEXT','FAILED',2,NULL,'2026-06-10 22:13:01','2026-06-10 22:13:01',1,-1,1,0),(5,'artifact-a5ae4b7f3e9c4cb586f07a63800b009e','session-e755e13d5a814d9fa248aac02eb0c1ef','round-2823036b80ce435494d41c1a6da2263a',0,'msg-e082d2019ce74863b2881e41c68c7223','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Error while extracting response for type [java.util.List<ai.platform.aiassist.service.ai.api.dto.AiModelConfigDTO>] and content type [application/json]','PLAIN_TEXT','FAILED',3,NULL,'2026-06-10 22:16:53','2026-06-10 22:16:53',1,-1,1,0),(6,'artifact-b9d762dcdfcb42ceba52df226011cb29','session-e755e13d5a814d9fa248aac02eb0c1ef','round-03cc5e779f7942258a22e69410d56c0f',0,'msg-c241664c1fc24b6caefdb6269e27868c','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Read timed out executing POST http://app-platform-ai-engine/aiEngine/api/v1/ai/execution/chat','PLAIN_TEXT','FAILED',4,NULL,'2026-06-10 22:19:33','2026-06-10 22:19:33',1,-1,1,0),(7,'artifact-07a93b926fb2459db5a96dded188aa16','session-e755e13d5a814d9fa248aac02eb0c1ef','round-2bd4f87b86d043338a9dccf57af7b229',0,'msg-4396618619ae410d96b8811944be603c','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Read timed out executing POST http://app-platform-ai-engine/aiEngine/api/v1/ai/execution/chat','PLAIN_TEXT','FAILED',5,NULL,'2026-06-10 22:20:41','2026-06-10 22:20:41',1,-1,1,0),(8,'artifact-87774654867c4e318a38277b2038e5ca','session-e755e13d5a814d9fa248aac02eb0c1ef','round-e267ab47163f40ed8c5846b84d020257',0,'msg-5c9acb247f824afeaba407f04baac48a','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Feign call failed, methodKey=AiChatExecutionApi#chat(ChatRequest), status=400, body={\"code\":10101,\"data\":null,\"timestamp\":1781101538400,\"traceId\":\"e2729cd9-2518-4e90-9d2f-7b99c5bad58c\",\"sign\":null,\"signKeyId\":null,\"msg\":\"非法参数异常：JSON parse error: Illegal character ((CTRL-CHAR, code 31)): only regular white space (\\\\r, \\\\n, \\\\t) is allowed between tokens\",\"status\":500}','PLAIN_TEXT','FAILED',6,NULL,'2026-06-10 22:25:39','2026-06-10 22:25:39',1,-1,1,0),(9,'artifact-bea587cbcfb6471a89c44b60232f6eef','session-e755e13d5a814d9fa248aac02eb0c1ef','round-b8b138686c684f35b277a6fbe4dc2e3b',0,'msg-7b5f00bb904040beb25df83682dc5de1','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Feign call failed, methodKey=AiChatExecutionApi#chat(ChatRequest), status=400, body={\"code\":10101,\"data\":null,\"timestamp\":1781101679380,\"traceId\":\"5e608345-ce47-4da0-ac76-709f7a15cd70\",\"sign\":null,\"signKeyId\":null,\"msg\":\"非法参数异常：JSON parse error: Illegal character ((CTRL-CHAR, code 31)): only regular white space (\\\\r, \\\\n, \\\\t) is allowed between tokens\",\"status\":500}','PLAIN_TEXT','FAILED',7,NULL,'2026-06-10 22:27:59','2026-06-10 22:27:59',1,-1,1,0),(10,'artifact-b46c20b534074f2597dfd4d2079158cf','session-e755e13d5a814d9fa248aac02eb0c1ef','round-0776bc2494444230a55aacb598964e33',0,'msg-ac0dff082fd543b488e1ae894d82e049','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Feign call failed, methodKey=AiChatExecutionApi#chat(ChatRequest), status=400, body={\"code\":10101,\"data\":null,\"timestamp\":1781101795170,\"traceId\":\"a8454676-71b3-41e3-8ed4-ea7ac23dd9a1\",\"sign\":null,\"signKeyId\":null,\"msg\":\"非法参数异常：JSON parse error: Illegal character ((CTRL-CHAR, code 31)): only regular white space (\\\\r, \\\\n, \\\\t) is allowed between tokens\",\"status\":500}','PLAIN_TEXT','FAILED',8,NULL,'2026-06-10 22:29:55','2026-06-10 22:29:55',1,-1,1,0),(11,'artifact-c84f848e11a143048101623c6bbb82d6','session-e755e13d5a814d9fa248aac02eb0c1ef','round-656cda80833641d9ae7a0444e457617d',0,'msg-d5cd1ede82634d1ca1d14832fd924a67','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Feign call failed, methodKey=AiChatExecutionApi#chat(ChatRequest), status=400, body={\"code\":10101,\"data\":null,\"timestamp\":1781101891517,\"traceId\":\"2f73a966-7bdc-4e54-8345-32788e7c44b0\",\"sign\":null,\"signKeyId\":null,\"msg\":\"非法参数异常：JSON parse error: Illegal character ((CTRL-CHAR, code 31)): only regular white space (\\\\r, \\\\n, \\\\t) is allowed between tokens\",\"status\":500}','PLAIN_TEXT','FAILED',9,NULL,'2026-06-10 22:31:32','2026-06-10 22:31:32',1,-1,1,0),(12,'artifact-e1b598c8e2494faa8ec1d47c2d91e2b7','session-e755e13d5a814d9fa248aac02eb0c1ef','round-e35054e3d0b0461896b20e26ac2d70d5',0,'msg-131a4230796c4d048eda35992eca4fa8','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Feign call failed, methodKey=AiChatExecutionApi#chat(ChatRequest), status=400, body={\"code\":10101,\"data\":null,\"timestamp\":1781101964834,\"traceId\":\"672bff7e-8fd3-4a04-a7a7-3cd49e941718\",\"sign\":null,\"signKeyId\":null,\"msg\":\"非法参数异常：JSON parse error: Illegal character ((CTRL-CHAR, code 31)): only regular white space (\\\\r, \\\\n, \\\\t) is allowed between tokens\",\"status\":500}','PLAIN_TEXT','FAILED',10,NULL,'2026-06-10 22:32:45','2026-06-10 22:32:45',1,-1,1,0),(13,'artifact-bb57cde56ee843b7985c3d554d0965a1','session-e755e13d5a814d9fa248aac02eb0c1ef','round-fb069d1409574fdca1841c8c99dc8ff8',0,'msg-915534e88070454786e3b9fe63124891','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Read timed out executing POST http://app-platform-ai-engine/aiEngine/api/v1/ai/execution/chat','PLAIN_TEXT','FAILED',11,NULL,'2026-06-10 22:34:33','2026-06-10 22:34:33',1,-1,1,0),(14,'artifact-10518b1f9cea4c30af8dffad7d77fd70','session-e755e13d5a814d9fa248aac02eb0c1ef','round-bdc3bb5907d84d9ebd2974616d5aa956',0,'msg-826c4d05e9ef4d8d866b5b745340f2b3','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Read timed out executing POST http://app-platform-ai-engine/aiEngine/api/v1/ai/execution/chat','PLAIN_TEXT','FAILED',12,NULL,'2026-06-10 22:34:40','2026-06-10 22:34:40',1,-1,1,0),(15,'artifact-e8b16ecd337c431d8fa8c508a7d0ef2d','session-e755e13d5a814d9fa248aac02eb0c1ef','round-639be716356b4db2ba1b8b0b45a1a53d',0,'msg-498c9d22462049e49e20d6526ce27f61','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Read timed out executing POST http://app-platform-ai-engine/aiEngine/api/v1/ai/execution/chat','PLAIN_TEXT','FAILED',13,NULL,'2026-06-10 22:36:09','2026-06-10 22:36:09',1,-1,1,0),(16,'artifact-739a0c77947345f280815387168e3c64','session-e755e13d5a814d9fa248aac02eb0c1ef','round-bdc43514cc5e4bac93a71807b81db136',0,'msg-73ceb7b5f56f44f88e5b9cae51beaae4','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Read timed out executing POST http://app-platform-ai-engine/aiEngine/api/v1/ai/execution/chat','PLAIN_TEXT','FAILED',14,NULL,'2026-06-10 22:36:36','2026-06-10 22:36:36',1,-1,1,0),(17,'artifact-98ade0ddb1e34458be3dc5a61fe37010','session-e755e13d5a814d9fa248aac02eb0c1ef','round-5f2ecb109abe42dfb291fbfec2f3c713',0,'msg-5ac1197ad7ca47f09d4a7babe8a00a02','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Read timed out executing POST http://app-platform-ai-engine/aiEngine/api/v1/ai/execution/chat','PLAIN_TEXT','FAILED',15,NULL,'2026-06-10 22:37:36','2026-06-10 22:37:36',1,-1,1,0),(18,'artifact-a12b57aab6e3414b9e7040d5f6190c87','session-e755e13d5a814d9fa248aac02eb0c1ef','round-060550b9a34443e0a9fe28d2f458399e',0,'msg-e6e3779da5ed41fe9c883ad15699dede','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Read timed out executing POST http://app-platform-ai-engine/aiEngine/api/v1/ai/execution/chat','PLAIN_TEXT','FAILED',16,NULL,'2026-06-10 22:37:55','2026-06-10 22:37:55',1,-1,1,0),(19,'artifact-54e1b2d5d95649908785eb80ab81328d','session-e755e13d5a814d9fa248aac02eb0c1ef','round-a34da0630f7f4aff90390a8eec907d2e',0,'msg-cfac37851669491c8bdf2063a67d9a59','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Feign call failed, methodKey=AiChatExecutionApi#chat(ChatRequest), status=400, body={\"code\":10101,\"data\":null,\"timestamp\":1781102321229,\"traceId\":\"b23ad8f9-06b6-4cb5-b371-1eeb38ba9a63\",\"sign\":null,\"signKeyId\":null,\"msg\":\"非法参数异常：JSON parse error: Illegal character ((CTRL-CHAR, code 31)): only regular white space (\\\\r, \\\\n, \\\\t) is allowed between tokens\",\"status\":500}','PLAIN_TEXT','FAILED',17,NULL,'2026-06-10 22:38:41','2026-06-10 22:38:41',1,-1,1,0),(20,'artifact-771cee77f3af4278bb7590bca7b97256','session-e755e13d5a814d9fa248aac02eb0c1ef','round-48676390bed64c21928951af29ea110b',0,'msg-88513270dba348a7b96309efd9abb7ab','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Feign call failed, methodKey=AiChatExecutionApi#chat(ChatRequest), status=400, body={\"code\":10101,\"data\":null,\"timestamp\":1781102567875,\"traceId\":\"bc559c53-7eba-4c67-a374-d85b8e794d89\",\"sign\":null,\"signKeyId\":null,\"msg\":\"非法参数异常：JSON parse error: Illegal character ((CTRL-CHAR, code 31)): only regular white space (\\\\r, \\\\n, \\\\t) is allowed between tokens\",\"status\":500}','PLAIN_TEXT','FAILED',18,NULL,'2026-06-10 22:42:48','2026-06-10 22:42:48',1,-1,1,0),(21,'artifact-66fb82c0a9134a63ba4a1da16d56c6a5','session-e755e13d5a814d9fa248aac02eb0c1ef','round-28205be8e9a3400dbb169a5bb0fc3d0e',0,'msg-6604e69a2d2a43f7a1ff14ca92edbe74','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Feign call failed, methodKey=AiChatExecutionApi#chat(ChatRequest), status=400, body={\"code\":10101,\"data\":null,\"timestamp\":1781102801449,\"traceId\":\"abf41077-af06-4b23-8ac6-7dab7699aef6\",\"sign\":null,\"signKeyId\":null,\"msg\":\"非法参数异常：JSON parse error: Illegal character ((CTRL-CHAR, code 31)): only regular white space (\\\\r, \\\\n, \\\\t) is allowed between tokens\",\"status\":500}','PLAIN_TEXT','FAILED',19,NULL,'2026-06-10 22:46:42','2026-06-10 22:46:42',1,-1,1,0),(22,'artifact-5dc66bb16cbb4b259bb167b5db56e2f1','session-e755e13d5a814d9fa248aac02eb0c1ef','round-ed1bbb191de8499c8da8f30835524171',0,'msg-d5695dd9c3ff42e894d2542773f627af','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Feign call failed, methodKey=AiChatExecutionApi#chat(ChatRequest), status=400, body={\"code\":10101,\"data\":null,\"timestamp\":1781102849674,\"traceId\":\"db2ee915-83ea-4f86-ab9b-7b408e3e245d\",\"sign\":null,\"signKeyId\":null,\"msg\":\"非法参数异常：JSON parse error: Illegal character ((CTRL-CHAR, code 31)): only regular white space (\\\\r, \\\\n, \\\\t) is allowed between tokens\",\"status\":500}','PLAIN_TEXT','FAILED',19,NULL,'2026-06-10 22:47:30','2026-06-10 22:47:30',1,-1,1,0),(23,'artifact-6cbdd0632fd54dceb60d108dc04c669f','session-e755e13d5a814d9fa248aac02eb0c1ef','round-5a4cc362bf4b4ecfb661ca93cd88bcbc',0,'msg-fba359d6c45e4ba69cf2f4d68d1fb63b','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Feign call failed, methodKey=AiChatExecutionApi#chat(ChatRequest), status=400, body={\"code\":10101,\"data\":null,\"timestamp\":1781139407094,\"traceId\":\"f6efa3c1-a622-4961-a355-6a643798701f\",\"sign\":null,\"signKeyId\":null,\"msg\":\"非法参数异常：JSON parse error: Illegal character ((CTRL-CHAR, code 31)): only regular white space (\\\\r, \\\\n, \\\\t) is allowed between tokens\",\"status\":500}','PLAIN_TEXT','FAILED',21,NULL,'2026-06-11 08:56:47','2026-06-11 08:56:47',1,-1,1,0),(24,'artifact-c3636a4d7ed446a8a749fdd2bf3bef25','session-e755e13d5a814d9fa248aac02eb0c1ef','round-e7665e0b4819497dbd2fad38df1e0c91',0,'msg-649b971f5c1d42418493214abb5fc2e8','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Feign call failed, methodKey=AiChatExecutionApi#chat(ChatRequest), status=400, body={\"code\":10101,\"data\":null,\"timestamp\":1781139690199,\"traceId\":\"fa9da438-9558-4665-b21e-3aa07be3b79c\",\"sign\":null,\"signKeyId\":null,\"msg\":\"非法参数异常：JSON parse error: Illegal character ((CTRL-CHAR, code 31)): only regular white space (\\\\r, \\\\n, \\\\t) is allowed between tokens\",\"status\":500}','PLAIN_TEXT','FAILED',22,NULL,'2026-06-11 09:01:31','2026-06-11 09:01:31',1,-1,1,0),(25,'artifact-e9e911adb98445f98c4b7eeaffd268f5','session-e755e13d5a814d9fa248aac02eb0c1ef','round-9ab6d91a6e7c4265993f88c063f93178',0,'msg-a38cfe524e674a28a5ef6b6cf39e7e3b','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Read timed out executing POST http://app-platform-ai-engine/aiEngine/api/v1/ai/execution/chat','PLAIN_TEXT','FAILED',23,NULL,'2026-06-11 09:05:39','2026-06-11 09:05:39',1,-1,1,0),(26,'artifact-b660a78eb87a4a35afeb3991490e2e67','session-e755e13d5a814d9fa248aac02eb0c1ef','round-b06c9c951cd644b1930de19a6a0dcf0f',0,'msg-e56aafe651054836bce89bfd7276db60','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Read timed out executing POST http://app-platform-ai-engine/aiEngine/api/v1/ai/execution/chat','PLAIN_TEXT','FAILED',24,NULL,'2026-06-11 09:09:42','2026-06-11 09:09:42',1,-1,1,0),(27,'artifact-90b9482f6d93490eb9be4cfdc3108815','session-e755e13d5a814d9fa248aac02eb0c1ef','round-fa7664a4073142cbb0b517b1806a2afd',0,'msg-ff53f6b1a27d40dfada43ec706151a53','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','Type definition error: [simple type, class org.athena.framework.web.vo.IR]','PLAIN_TEXT','FAILED',25,NULL,'2026-06-11 09:18:26','2026-06-11 09:18:26',1,-1,1,0),(28,'artifact-a60151f8a70f49cb8c021c70ecbff7c8','session-e755e13d5a814d9fa248aac02eb0c1ef','round-1d4198a928b6488894ae224fd40f743f',0,'msg-69c8fa8d14464b07b494e0ea3afb1f6f','QUERY_PLAN','PLAN','AI',1,'查询规划','{\"sessionTitle\":null,\"userGoal\":\"查询张三的基本情况信息\",\"analysisSummary\":\"用户需要获取张三的基础资料，可能涉及身份、职业、教育等维度数据\",\"analysisDimensions\":[\"个人身份信息\",\"职业背景\",\"教育经历\"],\"requiredContext\":[\"人员档案数据库结构\",\"字段定义与数据更新时效\"],\"sqlFocus\":[\"精准匹配姓名字段\",\"关联多表获取完整档案\"],\"risks\":[\"同名人员数据混淆\",\"部分敏感信息访问权限限制\"],\"needClarification\":false}','JSON','SUCCESS',26,'\"chatcmpl-24ea9808-677c-931c-8ff7-406177b45c65\"','2026-06-11 09:22:43','2026-06-11 09:22:43',1,-1,1,0),(29,'artifact-722a4a0fe7814a1b9e053b0acdc9eb57','session-e755e13d5a814d9fa248aac02eb0c1ef','round-1d4198a928b6488894ae224fd40f743f',0,'msg-69c8fa8d14464b07b494e0ea3afb1f6f','KNOWLEDGE_RESULT','KNOWLEDGE','AI',1,'知识检索结果','未检索到可用知识上下文。','MARKDOWN','SUCCESS',27,NULL,'2026-06-11 09:22:44','2026-06-11 09:22:44',1,-1,1,0),(30,'artifact-613f8d93abc640c99f6ba8c7c0e0da9f','session-e755e13d5a814d9fa248aac02eb0c1ef','round-1d4198a928b6488894ae224fd40f743f',0,'msg-69c8fa8d14464b07b494e0ea3afb1f6f','SQL_DRAFT','SQL_GEN','AI',1,'SQL 草案','-- 假设存在人员档案主表 personnel_info，涵盖身份、职业与教育等基础字段；若实际库表按维度拆分（如 basic_info, job_history, education_record），需通过唯一主键 personnel_id 进行 LEFT JOIN 关联\nSELECT \n    p.name AS 姓名,\n    p.gender AS 性别,\n    p.birth_date AS 出生日期,\n    p.id_card AS 身份证号,\n    p.department AS 所属部门,\n    p.position AS 当前岗位,\n    p.education_level AS 最高学历,\n    p.join_date AS 入职日期\nFROM personnel_info p\nWHERE p.name = \'张三\';','SQL','SUCCESS',28,'\"chatcmpl-4a529ffd-1313-9ac4-bef2-3af2ac34897d\"','2026-06-11 09:23:16','2026-06-11 09:23:16',1,-1,1,0),(31,'artifact-e87d4b7ad82843808e1b52c28e5b745a','session-e755e13d5a814d9fa248aac02eb0c1ef','round-1d4198a928b6488894ae224fd40f743f',0,'msg-69c8fa8d14464b07b494e0ea3afb1f6f','SQL_VALIDATED','SQL_VALIDATE','AI',1,'SQL 校验通过','-- 假设存在人员档案主表 personnel_info，涵盖身份、职业与教育等基础字段；若实际库表按维度拆分（如 basic_info, job_history, education_record），需通过唯一主键 personnel_id 进行 LEFT JOIN 关联\nSELECT \n    p.name AS 姓名,\n    p.gender AS 性别,\n    p.birth_date AS 出生日期,\n    p.id_card AS 身份证号,\n    p.department AS 所属部门,\n    p.position AS 当前岗位,\n    p.education_level AS 最高学历,\n    p.join_date AS 入职日期\nFROM personnel_info p\nWHERE p.name = \'张三\';','SQL','SUCCESS',29,NULL,'2026-06-11 09:23:17','2026-06-11 09:23:17',1,-1,1,0),(32,'artifact-865c78d2214f4d2387a387d54076bbac','session-e755e13d5a814d9fa248aac02eb0c1ef','round-1d4198a928b6488894ae224fd40f743f',0,'msg-69c8fa8d14464b07b494e0ea3afb1f6f','SQL_EXEC_RESULT','SQL_EXEC','AI',1,'SQL 执行结果','{\"executed\":false,\"status\":\"SKIPPED\",\"reason\":\"db-engine execute api is not integrated in current workflow\",\"sql\":\"-- 假设存在人员档案主表 personnel_info，涵盖身份、职业与教育等基础字段；若实际库表按维度拆分（如 basic_info, job_history, education_record），需通过唯一主键 personnel_id 进行 LEFT JOIN 关联\\nSELECT \\n    p.name AS 姓名,\\n    p.gender AS 性别,\\n    p.birth_date AS 出生日期,\\n    p.id_card AS 身份证号,\\n    p.department AS 所属部门,\\n    p.position AS 当前岗位,\\n    p.education_level AS 最高学历,\\n    p.join_date AS 入职日期\\nFROM personnel_info p\\nWHERE p.name = \'张三\';\",\"rows\":[]}','JSON','SKIPPED',30,'\"-- 假设存在人员档案主表 personnel_info，涵盖身份、职业与教育等基础字段；若实际库表按维度拆分（如 basic_info, job_history, education_record），需通过唯一主键 personnel_id 进行 LEFT JOIN 关联\\nSELECT \\n    p.name AS 姓名,\\n    p.gender AS 性别,\\n    p.birth_date AS 出生日期,\\n    p.id_card AS 身份证号,\\n    p.department AS 所属部门,\\n    p.position AS 当前岗位,\\n    p.education_level AS 最高学历,\\n    p.join_date AS 入职日期\\nFROM personnel_info p\\nWHERE p.name = \'张三\';\"','2026-06-11 09:23:18','2026-06-11 09:23:18',1,-1,1,0),(33,'artifact-ec46b5809b544bbbb3146c8c61a04b4f','session-e755e13d5a814d9fa248aac02eb0c1ef','round-1d4198a928b6488894ae224fd40f743f',0,'msg-69c8fa8d14464b07b494e0ea3afb1f6f','MODEL_RESPONSE_SNAPSHOT','RENDER','AI',1,'最终回答快照','当前工作流中数据库执行接口尚未接入，**仅为您生成了 SQL 查询草案，未实际执行查询**，因此无法返回“张三”的具体档案数据。\n\n📝 **查询草案（供参考）**\n```sql\nSELECT \n    p.name AS 姓名,\n    p.gender AS 性别,\n    p.birth_date AS 出生日期,\n    p.id_card AS 身份证号,\n    p.department AS 所属部门,\n    p.position AS 当前岗位,\n    p.education_level AS 最高学历,\n    p.join_date AS 入职日期\nFROM personnel_info p\nWHERE p.name = \'张三\';\n```\n\n💡 **主要假设**\n1. 系统存在统一的 `personnel_info` 人员档案主表，已集中存储身份、岗位、学历及入职时间等基础字段。\n2. 姓名“张三”在目标数据范围内具有唯一性，或您已确认需查询的具体对象。\n3. 当前查询账号具备访问身份证号等敏感字段的权限。\n\n🚀 **下一步建议**\n1. **启用执行环境**：接入并配置数据库执行 API 后，可直接运行该草案获取实时结果。\n2. **增加','MARKDOWN','SUCCESS',31,NULL,'2026-06-11 09:23:43','2026-06-11 09:23:43',1,-1,1,0),(34,'artifact-835e99bc1c794fddad5b4f629f42f7ea','session-e755e13d5a814d9fa248aac02eb0c1ef','round-1d4198a928b6488894ae224fd40f743f',0,'msg-69c8fa8d14464b07b494e0ea3afb1f6f','WORKFLOW_ERROR','RENDER','AI',1,'最终渲染失败','','PLAIN_TEXT','FAILED',32,NULL,'2026-06-11 09:23:45','2026-06-11 09:23:45',1,-1,1,0),(35,'artifact-9d45bb1b9f33421b86309b4d1083d3d9','session-59d4c661aa7040f0b2cc5498a9ed61b0','round-3508051b98154131a1ab4fbe15dd6003',1,'msg-a28c33c28858478295a352619aee6dc5','WORKFLOW_ERROR','PLAN','AI',1,'查询规划失败','','PLAIN_TEXT','FAILED',1,NULL,'2026-06-11 16:59:28','2026-06-11 16:59:28',1,-1,1,0);
/*!40000 ALTER TABLE `ai_chat_artifact` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_chat_message`
--

DROP TABLE IF EXISTS `ai_chat_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_code` varchar(64) NOT NULL COMMENT '消息编码',
  `round_code` varchar(64) NOT NULL COMMENT '轮次编码',
  `session_code` varchar(64) NOT NULL COMMENT '会话编码',
  `role` varchar(32) NOT NULL COMMENT '角色：USER/ASSISTANT',
  `actor_type` varchar(32) NOT NULL DEFAULT 'HUMAN' COMMENT '消息生产者类型',
  `message_type` varchar(32) NOT NULL DEFAULT 'USER_INPUT' COMMENT '消息业务类型',
  `display_level` varchar(32) NOT NULL DEFAULT 'VISIBLE' COMMENT '展示层级',
  `content_format` varchar(32) NOT NULL DEFAULT 'PLAIN_TEXT' COMMENT '内容格式',
  `parent_message_code` varchar(64) DEFAULT NULL COMMENT '父消息编码',
  `source_message_code` varchar(64) DEFAULT NULL COMMENT '源消息编码',
  `status` varchar(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '消息状态',
  `content` mediumtext NOT NULL COMMENT '消息内容',
  `sort_no` int NOT NULL DEFAULT '1' COMMENT '轮次内顺序',
  `ext_json` mediumtext COMMENT '扩展信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_code` (`message_code`),
  KEY `idx_round_code` (`round_code`),
  KEY `idx_session_code` (`session_code`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI聊天消息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_message`
--

LOCK TABLES `ai_chat_message` WRITE;
/*!40000 ALTER TABLE `ai_chat_message` DISABLE KEYS */;
INSERT INTO `ai_chat_message` VALUES (1,'msg-a3aca59484a3497294c46f6eaf822808','round-11e0431438704892a885886f90b27c1d','session-e755e13d5a814d9fa248aac02eb0c1ef','USER','HUMAN','USER_INPUT','VISIBLE','PLAIN_TEXT',NULL,NULL,'SUCCESS','我想查询张三的基本情况',1,NULL,'2026-06-10 21:26:52','2026-06-10 21:50:57',1,1,1,0),(2,'msg-1ed2bfc63d58433c8fd8163afc6ac802','round-dc7df352459045bd8eb4f17c8fe4219e','session-d8e776e5209145d9a6ba47c30b87de85','USER','HUMAN','USER_INPUT','VISIBLE','PLAIN_TEXT',NULL,NULL,'SUCCESS','我想查询张三的基本情况',1,NULL,'2026-06-10 21:32:24','2026-06-10 21:50:58',1,1,1,0),(28,'msg-ff53f6b1a27d40dfada43ec706151a53','round-fa7664a4073142cbb0b517b1806a2afd','session-e755e13d5a814d9fa248aac02eb0c1ef','USER','HUMAN','USER_INPUT','VISIBLE','PLAIN_TEXT','msg-a3aca59484a3497294c46f6eaf822808','msg-a3aca59484a3497294c46f6eaf822808','SUCCESS','我想查询张三的基本情况',2,NULL,'2026-06-11 09:17:51','2026-06-11 09:17:51',1,-1,1,0),(29,'msg-69c8fa8d14464b07b494e0ea3afb1f6f','round-1d4198a928b6488894ae224fd40f743f','session-e755e13d5a814d9fa248aac02eb0c1ef','USER','HUMAN','USER_INPUT','VISIBLE','PLAIN_TEXT','msg-ff53f6b1a27d40dfada43ec706151a53','msg-ff53f6b1a27d40dfada43ec706151a53','SUCCESS','我想查询张三的基本情况',3,NULL,'2026-06-11 09:21:08','2026-06-11 09:21:08',1,-1,1,0),(30,'msg-4abe1bf7cd7a483a8ce5d814ed7b89f8','round-1d4198a928b6488894ae224fd40f743f','session-e755e13d5a814d9fa248aac02eb0c1ef','ASSISTANT','AI','FINAL_ANSWER','VISIBLE','MARKDOWN','msg-69c8fa8d14464b07b494e0ea3afb1f6f','msg-69c8fa8d14464b07b494e0ea3afb1f6f','SUCCESS','当前工作流中数据库执行接口尚未接入，**仅为您生成了 SQL 查询草案，未实际执行查询**，因此无法返回“张三”的具体档案数据。\n\n📝 **查询草案（供参考）**\n```sql\nSELECT \n    p.name AS 姓名,\n    p.gender AS 性别,\n    p.birth_date AS 出生日期,\n    p.id_card AS 身份证号,\n    p.department AS 所属部门,\n    p.position AS 当前岗位,\n    p.education_level AS 最高学历,\n    p.join_date AS 入职日期\nFROM personnel_info p\nWHERE p.name = \'张三\';\n```\n\n💡 **主要假设**\n1. 系统存在统一的 `personnel_info` 人员档案主表，已集中存储身份、岗位、学历及入职时间等基础字段。\n2. 姓名“张三”在目标数据范围内具有唯一性，或您已确认需查询的具体对象。\n3. 当前查询账号具备访问身份证号等敏感字段的权限。\n\n🚀 **下一步建议**\n1. **启用执行环境**：接入并配置数据库执行 API 后，可直接运行该草案获取实时结果。\n2. **增加',4,NULL,'2026-06-11 09:23:42','2026-06-11 09:23:42',1,-1,1,0),(31,'msg-a28c33c28858478295a352619aee6dc5','round-3508051b98154131a1ab4fbe15dd6003','session-59d4c661aa7040f0b2cc5498a9ed61b0','USER','HUMAN','USER_INPUT','VISIBLE','PLAIN_TEXT',NULL,NULL,'SUCCESS','我想查询张三的基本情况',1,NULL,'2026-06-11 16:59:17','2026-06-11 16:59:17',1,-1,1,0);
/*!40000 ALTER TABLE `ai_chat_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_chat_node`
--

DROP TABLE IF EXISTS `ai_chat_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_node` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(64) NOT NULL COMMENT '节点编码',
  `name` varchar(128) NOT NULL COMMENT '节点名称',
  `type` varchar(64) NOT NULL COMMENT '节点类型',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `config` mediumtext COMMENT '节点目录配置JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_chat_node_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI节点目录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_node`
--

LOCK TABLES `ai_chat_node` WRITE;
/*!40000 ALTER TABLE `ai_chat_node` DISABLE KEYS */;
INSERT INTO `ai_chat_node` VALUES (1,'chat-message','ChatMessageNode','ChatMessageNode',1,'{\"summary\":\"初始化会话、轮次和用户消息，为后续节点准备完整执行上下文。\",\"executeMode\":\"SERIAL\"}','2026-06-15 23:33:37','2026-06-15 23:33:37',0,0,1,0),(2,'query-planning','QueryPlanningNode','QueryPlanningNode',1,'{\"summary\":\"解析用户目标并生成结构化查询规划。\",\"executeMode\":\"SERIAL\"}','2026-06-15 23:33:37','2026-06-15 23:33:37',0,0,1,0),(3,'knowledge-search','KnowledgeSearchNode','KnowledgeSearchNode',1,'{\"summary\":\"补充知识库口径和外部上下文。\",\"executeMode\":\"SERIAL\"}','2026-06-15 23:33:37','2026-06-15 23:33:37',0,0,1,0),(4,'sql-generate','SqlGenerateNode','SqlGenerateNode',1,'{\"summary\":\"基于规划和知识上下文生成候选SQL。\",\"executeMode\":\"SERIAL\"}','2026-06-15 23:33:37','2026-06-15 23:33:37',0,0,1,0),(5,'render','RenderNode','RenderNode',1,'{\"summary\":\"汇总规划、知识上下文和SQL预生成结果生成最终回答。\",\"executeMode\":\"SERIAL\"}','2026-06-15 23:33:37','2026-06-15 23:33:37',0,0,1,0);
/*!40000 ALTER TABLE `ai_chat_node` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_chat_round`
--

DROP TABLE IF EXISTS `ai_chat_round`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_round` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `round_code` varchar(64) NOT NULL COMMENT '轮次编码',
  `round_type` varchar(32) NOT NULL DEFAULT 'USER_QUERY' COMMENT '轮次类型',
  `parent_round_code` varchar(64) DEFAULT NULL COMMENT '父轮次编码',
  `session_code` varchar(64) NOT NULL COMMENT '会话编码',
  `user_id` bigint NOT NULL DEFAULT '0' COMMENT '用户ID',
  `model_code` varchar(64) DEFAULT NULL COMMENT '模型编码',
  `actual_model` varchar(128) DEFAULT NULL COMMENT '实际调用模型',
  `status` varchar(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_round_code` (`round_code`),
  KEY `idx_session_code` (`session_code`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI聊天轮次表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_round`
--

LOCK TABLES `ai_chat_round` WRITE;
/*!40000 ALTER TABLE `ai_chat_round` DISABLE KEYS */;
INSERT INTO `ai_chat_round` VALUES (1,'round-11e0431438704892a885886f90b27c1d','USER_QUERY',NULL,'session-e755e13d5a814d9fa248aac02eb0c1ef',0,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 21:26:52','2026-06-10 21:26:52',0,-1,1,0),(2,'round-dc7df352459045bd8eb4f17c8fe4219e','USER_QUERY',NULL,'session-d8e776e5209145d9a6ba47c30b87de85',0,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 21:32:23','2026-06-10 21:32:23',0,-1,1,0),(3,'round-be17f202108345deaed7117e0b59b1a9','FOLLOW_UP',NULL,'session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:03:01','2026-06-10 22:03:01',1,-1,1,0),(4,'round-2f56d65c17ce46aba91e70f9ad454cc9','FOLLOW_UP','round-be17f202108345deaed7117e0b59b1a9','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:07:55','2026-06-10 22:07:55',1,-1,1,0),(5,'round-fcef2a5d703848f8801e8fd319efc2ea','FOLLOW_UP','round-2f56d65c17ce46aba91e70f9ad454cc9','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:11:40','2026-06-10 22:11:40',1,-1,1,0),(6,'round-2823036b80ce435494d41c1a6da2263a','FOLLOW_UP','round-fcef2a5d703848f8801e8fd319efc2ea','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:16:42','2026-06-10 22:16:42',1,-1,1,0),(7,'round-03cc5e779f7942258a22e69410d56c0f','FOLLOW_UP','round-2823036b80ce435494d41c1a6da2263a','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:18:54','2026-06-10 22:18:54',1,-1,1,0),(8,'round-2bd4f87b86d043338a9dccf57af7b229','FOLLOW_UP','round-03cc5e779f7942258a22e69410d56c0f','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:20:35','2026-06-10 22:20:35',1,-1,1,0),(9,'round-e267ab47163f40ed8c5846b84d020257','FOLLOW_UP','round-2bd4f87b86d043338a9dccf57af7b229','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:25:37','2026-06-10 22:25:37',1,-1,1,0),(10,'round-b8b138686c684f35b277a6fbe4dc2e3b','FOLLOW_UP','round-e267ab47163f40ed8c5846b84d020257','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:27:58','2026-06-10 22:27:58',1,-1,1,0),(11,'round-0776bc2494444230a55aacb598964e33','FOLLOW_UP','round-b8b138686c684f35b277a6fbe4dc2e3b','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:29:54','2026-06-10 22:29:54',1,-1,1,0),(12,'round-656cda80833641d9ae7a0444e457617d','FOLLOW_UP','round-0776bc2494444230a55aacb598964e33','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:31:30','2026-06-10 22:31:30',1,-1,1,0),(13,'round-e35054e3d0b0461896b20e26ac2d70d5','FOLLOW_UP','round-656cda80833641d9ae7a0444e457617d','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:32:42','2026-06-10 22:32:42',1,-1,1,0),(14,'round-fb069d1409574fdca1841c8c99dc8ff8','FOLLOW_UP','round-e35054e3d0b0461896b20e26ac2d70d5','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:34:27','2026-06-10 22:34:27',1,-1,1,0),(15,'round-bdc3bb5907d84d9ebd2974616d5aa956','FOLLOW_UP','round-fb069d1409574fdca1841c8c99dc8ff8','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:34:34','2026-06-10 22:34:34',1,-1,1,0),(16,'round-639be716356b4db2ba1b8b0b45a1a53d','FOLLOW_UP','round-bdc3bb5907d84d9ebd2974616d5aa956','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:34:50','2026-06-10 22:34:50',1,-1,1,0),(17,'round-bdc43514cc5e4bac93a71807b81db136','FOLLOW_UP','round-639be716356b4db2ba1b8b0b45a1a53d','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:36:28','2026-06-10 22:36:28',1,-1,1,0),(18,'round-5f2ecb109abe42dfb291fbfec2f3c713','FOLLOW_UP','round-bdc43514cc5e4bac93a71807b81db136','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:37:24','2026-06-10 22:37:24',1,-1,1,0),(19,'round-060550b9a34443e0a9fe28d2f458399e','FOLLOW_UP','round-5f2ecb109abe42dfb291fbfec2f3c713','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:37:49','2026-06-10 22:37:49',1,-1,1,0),(20,'round-a34da0630f7f4aff90390a8eec907d2e','FOLLOW_UP','round-060550b9a34443e0a9fe28d2f458399e','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:38:40','2026-06-10 22:38:40',1,-1,1,0),(21,'round-48676390bed64c21928951af29ea110b','FOLLOW_UP','round-a34da0630f7f4aff90390a8eec907d2e','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:42:47','2026-06-10 22:42:47',1,-1,1,0),(22,'round-ed1bbb191de8499c8da8f30835524171','FOLLOW_UP','round-48676390bed64c21928951af29ea110b','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:45:48','2026-06-10 22:45:48',1,-1,1,0),(23,'round-28205be8e9a3400dbb169a5bb0fc3d0e','FOLLOW_UP','round-48676390bed64c21928951af29ea110b','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-10 22:46:40','2026-06-10 22:46:40',1,-1,1,0),(24,'round-5a4cc362bf4b4ecfb661ca93cd88bcbc','FOLLOW_UP','round-28205be8e9a3400dbb169a5bb0fc3d0e','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-11 08:56:45','2026-06-11 08:56:45',1,-1,1,0),(25,'round-e7665e0b4819497dbd2fad38df1e0c91','FOLLOW_UP','round-5a4cc362bf4b4ecfb661ca93cd88bcbc','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-11 09:01:28','2026-06-11 09:01:28',1,-1,1,0),(26,'round-9ab6d91a6e7c4265993f88c063f93178','FOLLOW_UP','round-e7665e0b4819497dbd2fad38df1e0c91','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-11 09:05:31','2026-06-11 09:05:31',1,-1,1,0),(27,'round-b06c9c951cd644b1930de19a6a0dcf0f','FOLLOW_UP','round-9ab6d91a6e7c4265993f88c063f93178','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-11 09:09:34','2026-06-11 09:09:34',1,-1,1,0),(28,'round-fa7664a4073142cbb0b517b1806a2afd','FOLLOW_UP','round-b06c9c951cd644b1930de19a6a0dcf0f','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-11 09:17:50','2026-06-11 09:17:50',1,-1,1,0),(29,'round-1d4198a928b6488894ae224fd40f743f','FOLLOW_UP','round-fa7664a4073142cbb0b517b1806a2afd','session-e755e13d5a814d9fa248aac02eb0c1ef',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-11 09:21:07','2026-06-11 09:21:07',1,-1,1,0),(30,'round-3508051b98154131a1ab4fbe15dd6003','USER_QUERY',NULL,'session-59d4c661aa7040f0b2cc5498a9ed61b0',1,'qwen3.6-plus','qwen3.6-plus','RUNNING','2026-06-11 16:59:16','2026-06-11 16:59:16',1,-1,1,0);
/*!40000 ALTER TABLE `ai_chat_round` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_chat_session`
--

DROP TABLE IF EXISTS `ai_chat_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_code` varchar(64) NOT NULL COMMENT '会话编码',
  `user_id` bigint NOT NULL DEFAULT '0' COMMENT '用户ID',
  `business_type` varchar(32) DEFAULT NULL COMMENT '业务类型',
  `session_name` varchar(128) DEFAULT NULL COMMENT '会话名称',
  `pinned` tinyint NOT NULL DEFAULT '0' COMMENT '是否置顶',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_code` (`session_code`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI聊天会话表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_session`
--

LOCK TABLES `ai_chat_session` WRITE;
/*!40000 ALTER TABLE `ai_chat_session` DISABLE KEYS */;
INSERT INTO `ai_chat_session` VALUES (1,'session-e755e13d5a814d9fa248aac02eb0c1ef',0,'GENERAL','我想查询张三的基本情况',0,'2026-06-10 21:26:52','2026-06-10 21:51:13',1,1,1,0),(2,'session-d8e776e5209145d9a6ba47c30b87de85',0,'GENERAL','我想查询张三的基本情况',0,'2026-06-10 21:32:22','2026-06-10 21:51:12',1,1,1,0),(3,'session-59d4c661aa7040f0b2cc5498a9ed61b0',1,'GENERAL','我想查询张三的基本情况',0,'2026-06-11 16:59:16','2026-06-11 16:59:16',1,-1,1,0);
/*!40000 ALTER TABLE `ai_chat_session` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_chat_skill`
--

DROP TABLE IF EXISTS `ai_chat_skill`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_skill` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(64) NOT NULL COMMENT 'Skill编码',
  `name` varchar(128) NOT NULL COMMENT 'Skill名称',
  `type` varchar(64) NOT NULL COMMENT 'Skill类型',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `config` mediumtext COMMENT 'Skill目录配置JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_chat_skill_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI Skill目录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_skill`
--

LOCK TABLES `ai_chat_skill` WRITE;
/*!40000 ALTER TABLE `ai_chat_skill` DISABLE KEYS */;
INSERT INTO `ai_chat_skill` VALUES (1,'business_term_resolve','术语解析','NODE_SKILL',1,'{\"summary\":\"抽取业务术语并回填给查询规划输入。\",\"supportedPhases\":[\"BEFORE_EXECUTE\"]}','2026-06-15 23:33:38','2026-06-15 23:33:38',0,0,1,0),(2,'time_range_normalize','时间范围归一化','NODE_SKILL',1,'{\"summary\":\"将自然语言时间转换成标准化时间范围。\",\"supportedPhases\":[\"BEFORE_EXECUTE\"]}','2026-06-15 23:33:38','2026-06-15 23:33:38',0,0,1,0),(3,'query_plan_review','查询规划审查','NODE_SKILL',1,'{\"summary\":\"对规划结果做结构和风险审查。\",\"supportedPhases\":[\"REVIEW_OUTPUT\"]}','2026-06-15 23:33:38','2026-06-15 23:33:38',0,0,1,0),(4,'sql_generation_policy','SQL生成规范','NODE_SKILL',1,'{\"summary\":\"注入SQL硬约束、软规范和白名单规则。\",\"supportedPhases\":[\"BEFORE_EXECUTE\"]}','2026-06-15 23:33:38','2026-06-15 23:33:38',0,0,1,0),(5,'user_preference_resolve','用户偏好解析','NODE_SKILL',1,'{\"summary\":\"提取用户偏好，补充SQL生成软约束。\",\"supportedPhases\":[\"BEFORE_EXECUTE\"]}','2026-06-15 23:33:38','2026-06-15 23:33:38',0,0,1,0);
/*!40000 ALTER TABLE `ai_chat_skill` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_chat_workflow`
--

DROP TABLE IF EXISTS `ai_chat_workflow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_workflow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(64) NOT NULL COMMENT '流程编码',
  `name` varchar(128) NOT NULL COMMENT '流程名称',
  `type` varchar(32) NOT NULL COMMENT '流程类型',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `config` mediumtext COMMENT '流程目录配置JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_chat_workflow_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI流程目录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_workflow`
--

LOCK TABLES `ai_chat_workflow` WRITE;
/*!40000 ALTER TABLE `ai_chat_workflow` DISABLE KEYS */;
INSERT INTO `ai_chat_workflow` VALUES (1,'ai-query-workflow','AI问数流程','QUERY',1,'{\"routeKey\":\"query\",\"sceneDesc\":\"面向智能问数、SQL生成、指标分析和数据解释。\",\"tags\":[\"问数\",\"SQL\",\"规划\"]}','2026-06-15 23:33:37','2026-06-15 23:33:37',0,0,1,0),(2,'general-chat-workflow','通用对话流程','CHAT',1,'{\"routeKey\":\"chat\",\"sceneDesc\":\"面向普通问答、总结、改写和通用助手场景。\",\"tags\":[\"对话\",\"总结\",\"通用\"]}','2026-06-15 23:33:37','2026-06-15 23:33:37',0,0,1,0),(3,'ai-app-workflow','AI应用流程','APP',1,'{\"routeKey\":\"app\",\"sceneDesc\":\"面向带工具编排、业务节点和多步骤执行的应用流程。\",\"tags\":[\"应用\",\"工具\",\"编排\"]}','2026-06-15 23:33:37','2026-06-15 23:33:37',0,0,1,0),(4,'workflow-audit','流程审查与回放','AUDIT',1,'{\"routeKey\":\"audit\",\"sceneDesc\":\"面向流程版本核对、节点回放、问题追踪和治理审计。\",\"tags\":[\"审计\",\"回放\",\"治理\"]}','2026-06-15 23:33:37','2026-06-15 23:33:37',0,0,1,0);
/*!40000 ALTER TABLE `ai_chat_workflow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_chat_workflow_config`
--

DROP TABLE IF EXISTS `ai_chat_workflow_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_workflow_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(64) NOT NULL COMMENT '配置编码',
  `workflow_code` varchar(64) NOT NULL COMMENT '流程编码',
  `name` varchar(128) NOT NULL COMMENT '配置名称',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `config` mediumtext COMMENT '流程运行配置JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_chat_workflow_config_code` (`code`),
  KEY `idx_ai_chat_workflow_config_workflow_code` (`workflow_code`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI流程配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_workflow_config`
--

LOCK TABLES `ai_chat_workflow_config` WRITE;
/*!40000 ALTER TABLE `ai_chat_workflow_config` DISABLE KEYS */;
INSERT INTO `ai_chat_workflow_config` VALUES (1,'ai-query-workflow-default','ai-query-workflow','AI问数默认配置',1,'{\"startNodeCode\":\"chat-message\",\"options\":{\"scene\":\"query\"}}','2026-06-15 23:33:39','2026-06-15 23:33:39',0,0,1,0);
/*!40000 ALTER TABLE `ai_chat_workflow_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_chat_workflow_config_node`
--

DROP TABLE IF EXISTS `ai_chat_workflow_config_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_workflow_config_node` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_code` varchar(64) NOT NULL COMMENT '流程配置编码',
  `node_code` varchar(64) NOT NULL COMMENT '节点编码',
  `sort` int NOT NULL DEFAULT '1' COMMENT '节点顺序',
  `next_code` varchar(64) DEFAULT NULL COMMENT '下一节点编码',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `config` mediumtext COMMENT '节点运行配置JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_chat_workflow_config_node` (`config_code`,`node_code`),
  KEY `idx_ai_chat_workflow_config_node_next_code` (`next_code`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI流程配置节点表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_workflow_config_node`
--

LOCK TABLES `ai_chat_workflow_config_node` WRITE;
/*!40000 ALTER TABLE `ai_chat_workflow_config_node` DISABLE KEYS */;
INSERT INTO `ai_chat_workflow_config_node` VALUES (1,'ai-query-workflow-default','chat-message',1,'query-planning',1,'{\"summary\":\"会话初始化\",\"executeMode\":\"SERIAL\",\"inputDefinitions\":[],\"outputDefinitions\":[],\"options\":{},\"ext\":{}}','2026-06-15 23:33:40','2026-06-15 23:33:40',0,0,1,0),(2,'ai-query-workflow-default','query-planning',2,'knowledge-search',1,'{\"summary\":\"查询规划\",\"executeMode\":\"SERIAL\",\"inputDefinitions\":[],\"outputDefinitions\":[],\"options\":{},\"ext\":{}}','2026-06-15 23:33:40','2026-06-15 23:33:40',0,0,1,0),(3,'ai-query-workflow-default','knowledge-search',3,'sql-generate',1,'{\"summary\":\"知识检索\",\"executeMode\":\"SERIAL\",\"inputDefinitions\":[],\"outputDefinitions\":[],\"options\":{},\"ext\":{}}','2026-06-15 23:33:40','2026-06-15 23:33:40',0,0,1,0),(4,'ai-query-workflow-default','sql-generate',4,'render',1,'{\"summary\":\"SQL生成\",\"executeMode\":\"SERIAL\",\"inputDefinitions\":[],\"outputDefinitions\":[],\"options\":{},\"ext\":{}}','2026-06-15 23:33:40','2026-06-15 23:33:40',0,0,1,0),(5,'ai-query-workflow-default','render',5,NULL,1,'{\"summary\":\"结果渲染\",\"executeMode\":\"SERIAL\",\"inputDefinitions\":[],\"outputDefinitions\":[],\"options\":{},\"ext\":{}}','2026-06-15 23:33:40','2026-06-15 23:33:40',0,0,1,0);
/*!40000 ALTER TABLE `ai_chat_workflow_config_node` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_chat_workflow_config_node_skill`
--

DROP TABLE IF EXISTS `ai_chat_workflow_config_node_skill`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_workflow_config_node_skill` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_code` varchar(64) NOT NULL COMMENT '流程配置编码',
  `node_code` varchar(64) NOT NULL COMMENT '节点编码',
  `skill_code` varchar(64) NOT NULL COMMENT 'Skill编码',
  `phase` varchar(32) NOT NULL COMMENT '挂接阶段',
  `sort` int NOT NULL DEFAULT '1' COMMENT 'Skill顺序',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `config` mediumtext COMMENT 'Skill挂接配置JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_chat_workflow_config_node_skill` (`config_code`,`node_code`,`skill_code`,`phase`),
  KEY `idx_ai_chat_workflow_config_node_skill_skill_code` (`skill_code`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI流程配置节点Skill表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_workflow_config_node_skill`
--

LOCK TABLES `ai_chat_workflow_config_node_skill` WRITE;
/*!40000 ALTER TABLE `ai_chat_workflow_config_node_skill` DISABLE KEYS */;
INSERT INTO `ai_chat_workflow_config_node_skill` VALUES (1,'ai-query-workflow-default','query-planning','business_term_resolve','BEFORE_EXECUTE',1,1,'{\"required\":false,\"options\":{},\"ext\":{}}','2026-06-15 23:33:40','2026-06-15 23:33:40',0,0,1,0),(2,'ai-query-workflow-default','query-planning','time_range_normalize','BEFORE_EXECUTE',2,1,'{\"required\":false,\"options\":{},\"ext\":{}}','2026-06-15 23:33:40','2026-06-15 23:33:40',0,0,1,0),(3,'ai-query-workflow-default','query-planning','query_plan_review','REVIEW_OUTPUT',3,1,'{\"required\":false,\"options\":{},\"ext\":{}}','2026-06-15 23:33:40','2026-06-15 23:33:40',0,0,1,0),(4,'ai-query-workflow-default','sql-generate','sql_generation_policy','BEFORE_EXECUTE',1,1,'{\"required\":false,\"options\":{},\"ext\":{}}','2026-06-15 23:33:40','2026-06-15 23:33:40',0,0,1,0),(5,'ai-query-workflow-default','sql-generate','user_preference_resolve','BEFORE_EXECUTE',2,1,'{\"required\":false,\"options\":{},\"ext\":{}}','2026-06-15 23:33:40','2026-06-15 23:33:40',0,0,1,0);
/*!40000 ALTER TABLE `ai_chat_workflow_config_node_skill` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_model_config`
--

DROP TABLE IF EXISTS `ai_model_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_model_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_code` varchar(64) NOT NULL COMMENT '模型编码',
  `model_name` varchar(128) NOT NULL COMMENT '模型名称',
  `provider_code` varchar(64) NOT NULL COMMENT '所属提供商编码',
  `api_model` varchar(128) NOT NULL COMMENT '提供商侧模型标识',
  `capability_tags` varchar(512) DEFAULT NULL COMMENT '能力标签，多个标签逗号分隔',
  `max_context_tokens` int DEFAULT NULL COMMENT '最大上下文Token数',
  `max_output_tokens` int DEFAULT NULL COMMENT '最大输出Token数',
  `temperature_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用温度参数：1启用，0禁用',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '启用状态：true启用，false禁用',
  `priority` int NOT NULL DEFAULT '100' COMMENT '优先级，数值越小优先级越高',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记：0未删除，1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_code` (`model_code`),
  KEY `idx_provider_code` (`provider_code`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI模型配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_model_config`
--

LOCK TABLES `ai_model_config` WRITE;
/*!40000 ALTER TABLE `ai_model_config` DISABLE KEYS */;
INSERT INTO `ai_model_config` VALUES (1,'Qwen-00001','千问3.7-plus','Qwen','qwen3.6-plus','Qwen, 阿里云',256,256,1,1,100,NULL,'2026-06-06 15:42:33','2026-06-07 21:59:55',0,0,1,0);
/*!40000 ALTER TABLE `ai_model_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_model_credential`
--

DROP TABLE IF EXISTS `ai_model_credential`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_model_credential` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `credential_code` varchar(64) NOT NULL COMMENT '密钥配置编码',
  `provider_code` varchar(64) NOT NULL COMMENT '提供商编码',
  `model_code` varchar(64) NOT NULL COMMENT '模型编码',
  `api_key_ciphertext` varchar(2048) NOT NULL COMMENT 'API Key密文',
  `api_key_masked` varchar(128) NOT NULL COMMENT 'API Key脱敏展示值',
  `key_version` int NOT NULL DEFAULT '1' COMMENT '密钥版本号',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '启用状态：true启用，false禁用',
  `expire_at` datetime DEFAULT NULL COMMENT '密钥过期时间',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记：0未删除，1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_credential_code` (`credential_code`),
  KEY `idx_provider_model` (`provider_code`,`model_code`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI模型密钥配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_model_credential`
--

LOCK TABLES `ai_model_credential` WRITE;
/*!40000 ALTER TABLE `ai_model_credential` DISABLE KEYS */;
INSERT INTO `ai_model_credential` VALUES (1,'QWen-0001','Qwen','qwen3.6-plus','sk-50239c760667494297501c8688ae8483','sk-5****8483',1,1,'2026-06-30 15:42:00','测试用例','2026-06-06 15:42:33','2026-06-06 15:42:33',0,0,1,0);
/*!40000 ALTER TABLE `ai_model_credential` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_provider_config`
--

DROP TABLE IF EXISTS `ai_provider_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_provider_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `provider_code` varchar(64) NOT NULL COMMENT '提供商编码',
  `provider_name` varchar(128) NOT NULL COMMENT '提供商名称',
  `base_url` varchar(512) NOT NULL COMMENT '提供商请求基础地址',
  `connect_timeout_ms` int NOT NULL DEFAULT '3000' COMMENT '连接超时时间（毫秒）',
  `read_timeout_ms` int NOT NULL DEFAULT '30000' COMMENT '读取超时时间（毫秒）',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '启用状态：true启用，false禁用',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记：0未删除，1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_code` (`provider_code`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI提供商配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_provider_config`
--

LOCK TABLES `ai_provider_config` WRITE;
/*!40000 ALTER TABLE `ai_provider_config` DISABLE KEYS */;
INSERT INTO `ai_provider_config` VALUES (1,'Qwen','阿里云','https://dashscope.aliyuncs.com/compatible-mode',3000,30000,1,'','2026-06-06 00:31:06','2026-06-06 00:31:06',0,0,1,0);
/*!40000 ALTER TABLE `ai_provider_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'platform_ai_assist_ai_chat'
--

--
-- Dumping routines for database 'platform_ai_assist_ai_chat'
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
