<script setup lang="ts">
import SystemSettingsPlaceholderSection from '../SystemSettingsPlaceholderSection.vue'
</script>

<template>
  <SystemSettingsPlaceholderSection
    title="数据源配置"
    description="这里先提供数据源模块的占位容器，后续可以继续接连接配置、状态检测和权限控制。"
    :bullets="['数据源接入', '连接状态监控', '权限与可见范围']"
  />
</template>
