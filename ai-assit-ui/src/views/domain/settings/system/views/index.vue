<script setup>
import SystemSidebar from '../../../../../components/SystemSidebar.vue'
import SystemContent from '../../../../../components/SystemContent.vue'
import { useSystemPage } from '../service'

const {
  sections,
  activeSection,
  sidebarCollapsed,
  switchSection,
  toggleSidebar
} = useSystemPage()
</script>

<template>
  <main class="system-page">
    <section class="system-hero">
      <div>
        <p class="eyebrow">System Center</p>
        <h1>系统配置中心</h1>
        <p class="hero-desc">把系统参数、常用组件和 AI 接入配置统一放进一个可扩展的页面壳层。</p>
      </div>
    </section>

    <section class="system-shell">
      <SystemSidebar
        :items="sections"
        :active-key="activeSection"
        :collapsed="sidebarCollapsed"
        @select="switchSection"
        @toggle="toggleSidebar"
      />

      <SystemContent :active-section="activeSection" />
    </section>
  </main>
</template>
