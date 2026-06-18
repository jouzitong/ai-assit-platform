-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: platform_ai_aissit
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
-- Current Database: `platform_ai_aissit`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `platform_ai_aissit` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `platform_ai_aissit`;

--
-- Table structure for table `sec_audit_log`
--

DROP TABLE IF EXISTS `sec_audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `category` varchar(32) NOT NULL COMMENT '日志分类',
  `action` varchar(64) NOT NULL COMMENT '操作动作',
  `result` varchar(16) NOT NULL COMMENT '操作结果',
  `user_id` bigint DEFAULT NULL COMMENT '用户主键ID',
  `username` varchar(64) DEFAULT NULL COMMENT '用户名',
  `tenant_id` varchar(64) DEFAULT NULL COMMENT '租户ID',
  `resource` varchar(255) DEFAULT NULL COMMENT '资源标识',
  `detail` varchar(500) DEFAULT NULL COMMENT '详情描述',
  `request_ip` varchar(64) DEFAULT NULL COMMENT '请求IP地址',
  `attributes_json` varchar(2000) DEFAULT NULL COMMENT '扩展属性JSON',
  `occurred_at` datetime(3) NOT NULL COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_sec_audit_log_occurred_at_id` (`occurred_at`,`id`),
  KEY `idx_sec_audit_log_user_id` (`user_id`),
  KEY `idx_sec_audit_log_category_action` (`category`,`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sec_audit_log`
--

LOCK TABLES `sec_audit_log` WRITE;
/*!40000 ALTER TABLE `sec_audit_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sec_audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sec_menu`
--

DROP TABLE IF EXISTS `sec_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `menu_code` varchar(64) NOT NULL COMMENT '菜单编码',
  `parent_code` varchar(64) DEFAULT NULL COMMENT '父级菜单编码',
  `menu_name` varchar(128) NOT NULL COMMENT '菜单名称',
  `path` varchar(255) DEFAULT NULL COMMENT '路由路径',
  `component` varchar(255) DEFAULT NULL COMMENT '前端组件路径',
  `permission_code` varchar(128) DEFAULT NULL COMMENT '关联权限编码',
  `sort_order` int DEFAULT NULL COMMENT '排序值',
  `status` varchar(16) NOT NULL COMMENT '菜单状态',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sec_menu_menu_code` (`menu_code`),
  KEY `idx_sec_menu_parent_code` (`parent_code`),
  KEY `idx_sec_menu_permission_code` (`permission_code`),
  KEY `idx_sec_menu_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sec_menu`
--

LOCK TABLES `sec_menu` WRITE;
/*!40000 ALTER TABLE `sec_menu` DISABLE KEYS */;
/*!40000 ALTER TABLE `sec_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sec_permission`
--

DROP TABLE IF EXISTS `sec_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `permission_code` varchar(128) NOT NULL COMMENT '权限编码',
  `permission_name` varchar(128) NOT NULL COMMENT '权限名称',
  `status` varchar(16) NOT NULL COMMENT '权限状态',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sec_permission_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sec_permission`
--

LOCK TABLES `sec_permission` WRITE;
/*!40000 ALTER TABLE `sec_permission` DISABLE KEYS */;
/*!40000 ALTER TABLE `sec_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sec_role`
--

DROP TABLE IF EXISTS `sec_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_code` varchar(64) NOT NULL COMMENT '角色编码',
  `role_name` varchar(128) NOT NULL COMMENT '角色名称',
  `status` varchar(16) NOT NULL COMMENT '角色状态',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sec_role_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sec_role`
--

LOCK TABLES `sec_role` WRITE;
/*!40000 ALTER TABLE `sec_role` DISABLE KEYS */;
/*!40000 ALTER TABLE `sec_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sec_role_permission`
--

DROP TABLE IF EXISTS `sec_role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_code` varchar(64) NOT NULL COMMENT '角色编码',
  `permission_code` varchar(128) NOT NULL COMMENT '权限编码',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sec_role_permission_role_perm` (`role_code`,`permission_code`),
  KEY `idx_sec_role_permission_role_code` (`role_code`),
  KEY `idx_sec_role_permission_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sec_role_permission`
--

LOCK TABLES `sec_role_permission` WRITE;
/*!40000 ALTER TABLE `sec_role_permission` DISABLE KEYS */;
/*!40000 ALTER TABLE `sec_role_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sec_user`
--

DROP TABLE IF EXISTS `sec_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(64) NOT NULL COMMENT '登录用户名',
  `display_name` varchar(128) DEFAULT NULL COMMENT '展示名称',
  `status` varchar(16) NOT NULL COMMENT '用户状态',
  `tenant_id` varchar(64) DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_sec_user_username` (`username`),
  KEY `idx_sec_user_tenant_id` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sec_user`
--

LOCK TABLES `sec_user` WRITE;
/*!40000 ALTER TABLE `sec_user` DISABLE KEYS */;
INSERT INTO `sec_user` VALUES (1,'admin','系统管理员','ENABLED',NULL);
/*!40000 ALTER TABLE `sec_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sec_user_credential`
--

DROP TABLE IF EXISTS `sec_user_credential`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_user_credential` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户主键ID',
  `credential_type` varchar(32) NOT NULL COMMENT '凭据类型',
  `password_hash` varchar(255) NOT NULL COMMENT '密码哈希值',
  `password_algo` varchar(32) DEFAULT NULL COMMENT '密码算法',
  `password_salt` varchar(255) DEFAULT NULL COMMENT '密码盐值',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sec_user_credential_user_type` (`user_id`,`credential_type`),
  KEY `idx_sec_user_credential_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户凭据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sec_user_credential`
--

LOCK TABLES `sec_user_credential` WRITE;
/*!40000 ALTER TABLE `sec_user_credential` DISABLE KEYS */;
INSERT INTO `sec_user_credential` VALUES (1,1,'PASSWORD','Admin@123456','PLAINTEXT',NULL);
/*!40000 ALTER TABLE `sec_user_credential` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sec_user_role`
--

DROP TABLE IF EXISTS `sec_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户主键ID',
  `role_code` varchar(64) NOT NULL COMMENT '角色编码',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sec_user_role_user_role` (`user_id`,`role_code`),
  KEY `idx_sec_user_role_user_id` (`user_id`),
  KEY `idx_sec_user_role_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sec_user_role`
--

LOCK TABLES `sec_user_role` WRITE;
/*!40000 ALTER TABLE `sec_user_role` DISABLE KEYS */;
/*!40000 ALTER TABLE `sec_user_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `system_settings`
--

DROP TABLE IF EXISTS `system_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `setting_key` varchar(128) NOT NULL COMMENT '系统配置唯一键',
  `description` varchar(512) DEFAULT NULL COMMENT '配置说明',
  `setting_value` text COMMENT '配置值',
  `value_type` varchar(32) NOT NULL DEFAULT 'STRING' COMMENT '配置值类型',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_setting_key` (`setting_key`),
  KEY `idx_value_type` (`value_type`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `system_settings`
--

LOCK TABLES `system_settings` WRITE;
/*!40000 ALTER TABLE `system_settings` DISABLE KEYS */;
INSERT INTO `system_settings` VALUES (1,'test.db','测试用的','啊dasd啊','STRING',1,'2026-06-18 19:44:31','2026-06-18 19:44:31',1,1,1);
/*!40000 ALTER TABLE `system_settings` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'platform_ai_aissit'
--

--
-- Dumping routines for database 'platform_ai_aissit'
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
