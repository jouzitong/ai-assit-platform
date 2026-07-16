<script setup lang="ts">
import CatalogListView from '../components/CatalogListView.vue'
import { deleteTool, listTools, publishTool, validateTool } from '../api/tools'
</script>

<template>
  <CatalogListView
    title="Tool 管理"
    description="管理 Tool JSON Schema、权限与审批策略，以及 Python、TypeScript、HTTP 或 MCP Runtime Binding。"
    resource-label="Tool"
    route-base="/settings/system/tools"
    :list-request="listTools"
    :delete-request="deleteTool"
    :validate-request="validateTool"
    :publish-request="publishTool"
  />
</template>
