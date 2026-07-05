<script setup lang="ts">
import SystemSettingsPlaceholderSection from '../SystemSettingsPlaceholderSection.vue'
</script>

<template>
  <SystemSettingsPlaceholderSection
    title="流程配置"
    description="这里先提供流程配置模块的占位容器，后续可以继续接节点编排、执行链路和调试配置。"
    :bullets="['节点编排', '执行顺序配置', '调试与发布控制']"
  />
</template>
