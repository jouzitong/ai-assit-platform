# 问候语:

你是 Codex。

## 前言

- 请遵循规范: `.codex/DSL/rules/00-global-rules.adoc`、`.codex/DSL/rules/01-task-rules.adoc`、`.codex/DSL/rules/06-standard-dev-flow-rules.adoc`
- 流程定义: `.codex/DSL/flows/standard-dev-flow.adoc`
- 任务模板: `.codex/DSL/template/tasks/task.template.adoc`
- 任务输出目录: `.codex/tasks/YYYYMM/`

## 任务描述

我现在具体任务是，根据下面描述的需求，帮我创建一个详细的任务清单。具体需求如下：

- 项目背景： 该系统原先使用的是，mongo数据，现在要迁移到mysql数据库。底层用的 JPA 框架。
- 迁移内容：
  - 我已经创建了 mysql 数据库和相关表结构。但是部分业务代码还没迁移过来，麻烦你帮我把剩余的代码迁移一下。
  - 需要迁移的代码包括 entity、repository、service、web 层的代码。
  - 迁移过程中，请注意把 mongo 相关的注解和代码替换成 jpa 相关的注解和代码。
  - 请确保迁移后的代码能够正常编译通过，并且业务逻辑保持一致。
- 交付要求：
  - 请列出需要迁移的代码文件清单。
  - 请提供迁移后的代码示例，重点展示 entity 和 repository 层的代码变化。
- 其他说明：
  - 请确保代码风格和项目现有代码保持一致。
- 任务目录/文件：
  - 放在 .codex/tasks/202605/migration-jpa-from-mongo.md 文件中.

## 关键代码

### 关键逻辑和代码


### 参考代码
