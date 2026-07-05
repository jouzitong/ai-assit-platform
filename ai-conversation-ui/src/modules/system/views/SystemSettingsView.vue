<script setup lang="ts">
import { Connection, Cpu, DataAnalysis, Setting, Share } from '@element-plus/icons-vue'
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SystemSettingsSidebar from '../components/SystemSettingsSidebar.vue'
import AiPlatformSection from '../components/sections/AiPlatformSection.vue'
import ComponentManageSection from '../components/sections/ComponentManageSection.vue'
import DataSourceSection from '../components/sections/DataSourceSection.vue'
import SystemParamsSection from '../components/sections/SystemParamsSection.vue'
import WorkflowSection from '../components/sections/WorkflowSection.vue'

type SettingsSection = {
  key: string
  label: string
  icon: unknown
  title: string
  description: string
  component: unknown
}

const router = useRouter()
const route = useRoute()

const sections: SettingsSection[] = [
  {
    key: 'system-params',
    label: '系统参数',
    icon: Setting,
    title: '系统参数',
    description: '集中管理全局运行参数、默认开关和页面级配置。',
    component: SystemParamsSection,
  },
  {
    key: 'data-source',
    label: '数据源',
    icon: Connection,
    title: '数据源配置',
    description: '维护数据库连接、授权信息和查询可用范围。',
    component: DataSourceSection,
  },
  {
    key: 'component-manage',
    label: '组件管理',
    icon: DataAnalysis,
    title: '组件管理',
    description: '管理渲染组件、业务组件和页面素材的装配能力。',
    component: ComponentManageSection,
  },
  {
    key: 'ai-platform',
    label: 'AI平台管理',
    icon: Cpu,
    title: 'AI 平台管理',
    description: '管理模型、提供方、密钥和 AI 平台级别配置。',
    component: AiPlatformSection,
  },
  {
    key: 'workflow',
    label: '流程配置',
    icon: Share,
    title: '流程配置',
    description: '配置工作流节点、执行链路和流程编排策略。',
    component: WorkflowSection,
  },
]

const activeSection = computed(() => {
  const section = typeof route.params.section === 'string' ? route.params.section : ''
  return sections.some((item) => item.key === section) ? section : sections[0].key
})

const currentSection = computed(() => sections.find((item) => item.key === activeSection.value) || sections[0])
const hideHeroSections = new Set(['component-manage', 'system-params'])

async function navigateToSection(sectionKey: string) {
  if (activeSection.value === sectionKey) {
    return
  }
  await router.push(`/settings/system/${sectionKey}`)
}

async function navigateHome() {
  await router.push('/')
}

onMounted(() => {
  if (typeof route.params.section !== 'string' || !sections.some((item) => item.key === route.params.section)) {
    void router.replace(`/settings/system/${sections[0].key}`)
  }
})
</script>

<template>
  <div class="system-settings-shell">
    <SystemSettingsSidebar
      :sections="sections"
      :active-section="activeSection"
      @navigate-home="navigateHome"
      @select-section="navigateToSection"
    />

    <main class="system-settings-content">
      <header v-if="!hideHeroSections.has(activeSection)" class="system-settings-hero">
        <h2>{{ currentSection.title }}</h2>
        <p>{{ currentSection.description }}</p>
      </header>

      <component :is="currentSection.component" />
    </main>
  </div>
</template>

<style scoped>
.system-settings-shell {
  display: grid;
  grid-template-columns: 236px minmax(0, 1fr);
  height: 100vh;
  background: #f4f6f8;
  overflow: hidden;
}

.system-settings-content {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  padding: 24px;
  overflow-y: auto;
}

.system-settings-hero {
  padding: 4px 0 16px;
}

.system-settings-hero h2 {
  margin: 6px 0 4px;
  color: #111827;
}

.system-settings-hero p {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.55;
}

@media (max-width: 960px) {
  .system-settings-shell {
    grid-template-columns: 1fr;
    height: auto;
    overflow: visible;
  }

  .system-settings-content {
    overflow-y: visible;
  }
}
</style>
