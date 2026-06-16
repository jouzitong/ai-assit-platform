<script setup>
import NodeList from './ai-flow/nodeList.vue'
import SkillList from './ai-flow/skillList.vue'
import WorkflowList from './ai-flow/workflowList.vue'
import { useAiFlowPage } from '../service/ai-flow'

const { sectionTabs, activeSection, sectionMeta, switchSection } = useAiFlowPage()
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

    <WorkflowList v-if="activeSection === 'workflow'" :section-meta="sectionMeta" />
    <NodeList v-else-if="activeSection === 'node'" :section-meta="sectionMeta" />
    <SkillList v-else :section-meta="sectionMeta" />
  </div>
</template>

<style scoped src="../styles/ai-flow.css"></style>
