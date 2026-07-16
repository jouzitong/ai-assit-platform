<script setup lang="ts">
import CatalogListView from '../components/CatalogListView.vue'
import { deleteWorkflow, listWorkflows, publishWorkflow, validateWorkflow } from '../api/workflows'
</script>

<template>
  <CatalogListView
    title="Artifact Workflow"
    description="定义产出物契约、检查规则和完成策略，Agent 按验收目标自主协作。"
    resource-label="Workflow"
    route-base="/settings/system/workflows"
    :list-request="listWorkflows"
    :delete-request="deleteWorkflow"
    :validate-request="validateWorkflow"
    :publish-request="publishWorkflow"
  />
</template>
