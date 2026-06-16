<script setup>
import NodeList from './ai-flow/nodeList.vue'
import SkillList from './ai-flow/skillList.vue'
import WorkflowList from './ai-flow/workflowList.vue'
import { useAiFlowPage } from '../service/ai-flow'

const {
  sectionTabs,
  activeSection,
  sectionMeta,
  loading,
  errorMessage,
  notice,
  dialogState,
  confirmState,
  switchSection,
  openCreateDialog,
  openEditDialog,
  closeDialog,
  submitDialog,
  openDeleteConfirm,
  closeDeleteConfirm,
  submitDeleteConfirm
} = useAiFlowPage()
</script>

<template>
  <div class="ai-flow-page">
    <header class="content-head">
      <div class="flow-head-main">
        <p class="crumb">系统设置 / AI流程配置</p>
        <h1>AI 流程配置</h1>
        <p class="section-desc">
          这里用于统一管理 AI 工作流的节点编排、技能挂载、SQL 生成规范和偏好策略。当前先按流程类型做列表入口。
        </p>
      </div>

      <div class="flow-head-sections" aria-label="AI流程配置分区">
        <button
          v-for="item in sectionTabs"
          :key="item.key"
          type="button"
          class="flow-head-section"
          :class="{ active: activeSection === item.key }"
          @click="switchSection(item.key)"
        >
          <strong>{{ item.title }}</strong>
          <span>{{ item.desc }}</span>
        </button>
      </div>
    </header>

    <section v-if="errorMessage" class="page-state error">
      <h3>列表加载失败</h3>
      <p>{{ errorMessage }}</p>
    </section>

    <section v-else-if="loading" class="page-state">
      <p>正在加载流程配置...</p>
    </section>

    <WorkflowList
      v-else-if="activeSection === 'workflow'"
      :section-meta="sectionMeta"
      @create="openCreateDialog"
      @edit="openEditDialog"
      @delete="openDeleteConfirm"
    />
    <NodeList
      v-else-if="activeSection === 'node'"
      :section-meta="sectionMeta"
      @create="openCreateDialog"
      @edit="openEditDialog"
      @delete="openDeleteConfirm"
    />
    <SkillList v-else :section-meta="sectionMeta" @create="openCreateDialog" @edit="openEditDialog" @delete="openDeleteConfirm" />

    <transition name="fade">
      <div v-if="notice.visible" class="toast" :class="notice.type">
        {{ notice.text }}
      </div>
    </transition>

    <div v-if="dialogState.visible" class="floating-mask" @click.self="closeDialog">
      <section class="floating-panel">
        <div class="floating-head">
          <div>
            <p class="eyebrow">配置维护</p>
            <h3>{{ dialogState.mode === 'create' ? '新增' : '编辑' }}{{ dialogState.entityType === 'workflow' ? '流程' : dialogState.entityType === 'node' ? '节点类型' : 'Skill 类型' }}</h3>
          </div>
          <button type="button" class="ghost-action" @click="closeDialog">关闭</button>
        </div>

        <div class="form-grid">
          <label class="field">
            <span>编码</span>
            <input v-model.trim="dialogState.form.code" type="text" placeholder="请输入编码" :disabled="dialogState.mode === 'edit'" />
          </label>

          <label v-if="dialogState.entityType === 'workflow'" class="field">
            <span>路由 Key</span>
            <input v-model.trim="dialogState.form.key" type="text" placeholder="例如：query" />
          </label>

          <label class="field">
            <span>名称</span>
            <input v-model.trim="dialogState.form.name" type="text" placeholder="请输入名称" />
          </label>

          <label class="field">
            <span>类型</span>
            <input v-model.trim="dialogState.form.type" type="text" placeholder="请输入类型" />
          </label>

          <label v-if="dialogState.entityType === 'node'" class="field">
            <span>执行方式</span>
            <select v-model="dialogState.form.executeMode">
              <option value="SERIAL">SERIAL</option>
              <option value="PARALLEL">PARALLEL</option>
              <option value="LOOP">LOOP</option>
            </select>
          </label>

          <label class="field">
            <span>状态</span>
            <select v-model="dialogState.form.enabled">
              <option :value="true">启用</option>
              <option :value="false">停用</option>
            </select>
          </label>

          <label v-if="dialogState.entityType === 'workflow'" class="field field-full">
            <span>场景说明</span>
            <textarea v-model.trim="dialogState.form.scene" rows="4" placeholder="请输入流程场景说明" />
          </label>

          <label v-if="dialogState.entityType === 'workflow'" class="field field-full">
            <span>标签</span>
            <input v-model.trim="dialogState.form.tagsText" type="text" placeholder="多个标签用英文逗号分隔" />
          </label>

          <label v-if="dialogState.entityType === 'skill'" class="field field-full">
            <span>支持阶段</span>
            <input
              v-model.trim="dialogState.form.supportedPhasesText"
              type="text"
              placeholder="多个阶段用英文逗号分隔，例如 BEFORE_EXECUTE,AFTER_EXECUTE"
            />
          </label>

          <label v-if="dialogState.entityType !== 'workflow'" class="field field-full">
            <span>摘要</span>
            <textarea v-model.trim="dialogState.form.summary" rows="4" placeholder="请输入摘要" />
          </label>
        </div>

        <p v-if="dialogState.error" class="dialog-error">{{ dialogState.error }}</p>

        <div class="panel-actions">
          <button type="button" class="ghost-action" @click="closeDialog">取消</button>
          <button type="button" class="mini-action primary" :disabled="dialogState.saving" @click="submitDialog">
            {{ dialogState.saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </section>
    </div>

    <div v-if="confirmState.visible" class="floating-mask" @click.self="closeDeleteConfirm">
      <section class="floating-panel floating-panel-confirm">
        <div class="floating-head">
          <div>
            <p class="eyebrow">删除确认</p>
            <h3>确认删除“{{ confirmState.title }}”</h3>
          </div>
        </div>
        <p class="confirm-copy">删除后将立即请求后端接口，相关配置将不可恢复。</p>
        <div class="panel-actions">
          <button type="button" class="ghost-action" @click="closeDeleteConfirm">取消</button>
          <button type="button" class="mini-action danger-fill" :disabled="confirmState.deleting" @click="submitDeleteConfirm">
            {{ confirmState.deleting ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped src="../styles/ai-flow.css"></style>
