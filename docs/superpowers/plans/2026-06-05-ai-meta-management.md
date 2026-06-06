# AI Meta Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current mock AI settings page with a real `Provider / Model` management console backed by `app-platform-ai-chat/meta`, and add a new aggregated `Model + Credential` management API.

**Architecture:** Keep `Provider` on the existing Athena CRUD path. Add a new `AiModelManage` aggregate path in `meta` with a custom MyBatis mapper/XML for joined reads and a domain service for transactional writes across `AiModelConfigEntity` and `AiModelCredentialEntity`. Rebuild the frontend page as two tabs that call real APIs and edit through dialogs.

**Tech Stack:** Spring Boot, MyBatis-Plus, Athena data/web starters, Vue 3, Element Plus, Vite

---

### Task 1: Add aggregated model-management backend contract

**Files:**
- Create: `app/app-platform-ai-chat/meta/src/main/java/ai/platform/aiassit/chat/meta/entity/dto/AiModelManageDTO.java`
- Create: `app/app-platform-ai-chat/meta/src/main/java/ai/platform/aiassit/chat/meta/entity/req/AiModelManageQueryRequest.java`

- [ ] Define one aggregate DTO carrying model fields, credential summary fields, and a write-only API key input field.
- [ ] Define one query request carrying page info, keyword, provider filter, enabled filter, and model code filter.

### Task 2: Add custom aggregate mapper and query XML

**Files:**
- Create: `app/app-platform-ai-chat/meta/src/main/java/ai/platform/aiassit/chat/meta/mapper/AiModelManageMapper.java`
- Create: `app/app-platform-ai-chat/meta/src/main/resources/mapper/AiModelManageMapper.xml`

- [ ] Add joined page/detail SQL from `ai_model_config` to the latest undeleted `ai_model_credential`.
- [ ] Add helper SQL to resolve the credential row bound to a model code for update/delete orchestration.

### Task 3: Add transactional domain service and controller

**Files:**
- Create: `app/app-platform-ai-chat/meta/src/main/java/ai/platform/aiassit/chat/meta/domainservice/AiModelManageDomainService.java`
- Create: `app/app-platform-ai-chat/meta/src/main/java/ai/platform/aiassit/chat/meta/domainservice/impl/AiModelManageDomainServiceImpl.java`
- Create: `app/app-platform-ai-chat/meta/src/main/java/ai/platform/aiassit/chat/meta/controller/AiModelManageController.java`

- [ ] Implement aggregate page/get/add/update/edit/delete operations.
- [ ] Reuse existing `AiModelConfigService` and `AiModelCredentialService` for persistence where possible.
- [ ] Keep writes transactional and derive `apiKeyMasked` from user input when a new key is provided.

### Task 4: Replace frontend mock page with Provider / Model management

**Files:**
- Modify: `ai-assit-ui/src/api/aiChat.js`
- Modify: `ai-assit-ui/src/views/domain/settings/system/pages/ai/index.vue`

- [ ] Add provider CRUD requests plus new model-manage aggregate requests.
- [ ] Replace local mock state with real remote state, tabs, filters, tables, pagination, and dialogs.
- [ ] Keep the current shell layout fixes intact while replacing only page-local logic/styles.

### Task 5: Verify and sync

**Files:**
- Modify: working tree as required

- [ ] Run `mvn -pl app/app-platform-ai-chat/meta -am -DskipTests compile`.
- [ ] Run `cd ai-assit-ui && npm run build`.
- [ ] Run `codegraph sync`.
- [ ] Inspect `http://localhost:5173/settings/system/ai` in the in-app browser and fix visible regressions if present.
