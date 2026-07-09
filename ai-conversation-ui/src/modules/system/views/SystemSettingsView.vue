<script setup lang="ts">
import { ArrowLeftBold, ArrowRightBold, Connection, Cpu, DataAnalysis, Setting, Share, Tickets } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SystemSettingsSidebar from '../components/SystemSettingsSidebar.vue'
import AiPlatformSection from '../components/sections/AiPlatformSection.vue'
import ComponentManageSection from '../components/sections/ComponentManageSection.vue'
import DataSourceSection from '../components/sections/DataSourceSection.vue'
import DataSourceTableSection from '../components/sections/DataSourceTableSection.vue'
import ErrorCodeSection from '../components/sections/ErrorCodeSection.vue'
import KbDocumentManageSection from '../components/sections/KbDocumentManageSection.vue'
import MetadataConfigSection from '../components/sections/MetadataConfigSection.vue'
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
const sidebarCollapsed = ref(false)

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
    key: 'err-code',
    label: '错误码管理',
    icon: Tickets,
    title: '错误码管理',
    description: '维护系统错误码、HTTP 状态和国际化文案。',
    component: ErrorCodeSection,
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
    key: 'metadata-config',
    label: '元数据配置',
    icon: DataAnalysis,
    title: '元数据配置',
    description: '统一维护平台元数据项、分类结构和配置内容。',
    component: MetadataConfigSection,
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

const hasDataSourceDetail = computed(() => activeSection.value === 'data-source' && typeof route.params.sourceKey === 'string' && route.params.sourceKey.trim().length > 0)
const hasAiPlatformKbDetail = computed(() => activeSection.value === 'ai-platform' && typeof route.params.sourceKey === 'string' && route.params.sourceKey.trim().length > 0)
const currentSection = computed(() => sections.find((item) => item.key === activeSection.value) || sections[0])

async function navigateToSection(sectionKey: string) {
  if (activeSection.value === sectionKey) {
    return
  }
  await router.push(`/settings/system/${sectionKey}`)
}

async function navigateHome() {
  await router.push('/')
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

onMounted(() => {
  if (typeof route.params.section !== 'string' || !sections.some((item) => item.key === route.params.section)) {
    void router.replace(`/settings/system/${sections[0].key}`)
  }
})
</script>

<template>
  <div
    class="system-settings-shell"
    :class="{ 'system-settings-shell--collapsed': sidebarCollapsed }"
    :style="{ '--system-sidebar-width': sidebarCollapsed ? '88px' : '236px' }"
  >
    <SystemSettingsSidebar
      :sections="sections"
      :active-section="activeSection"
      :collapsed="sidebarCollapsed"
      @navigate-home="navigateHome"
      @select-section="navigateToSection"
    />

    <button
      class="system-settings-shell__toggle"
      type="button"
      :aria-label="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
      :title="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
      @click="toggleSidebar"
    >
      <el-icon>
        <ArrowRightBold v-if="sidebarCollapsed" />
        <ArrowLeftBold v-else />
      </el-icon>
    </button>

    <main class="system-settings-content">
      <component
        :is="hasDataSourceDetail ? DataSourceTableSection : hasAiPlatformKbDetail ? KbDocumentManageSection : currentSection.component"
      />
    </main>
  </div>
</template>

<style scoped>
.system-settings-shell {
  position: relative;
  display: grid;
  grid-template-columns: var(--system-sidebar-width) minmax(0, 1fr);
  height: 100vh;
  background: var(--system-shell-bg);
  overflow: hidden;
}

.system-settings-shell__toggle {
  position: absolute;
  top: 28px;
  left: calc(var(--system-sidebar-width) - 16px);
  z-index: 15;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid var(--system-sidebar-border);
  border-radius: 50%;
  background: var(--system-surface-solid);
  color: var(--system-text-soft);
  box-shadow: var(--system-shadow);
  cursor: pointer;
  transition: left 0.2s ease, color 0.2s ease, background-color 0.2s ease;
}

.system-settings-shell__toggle:hover {
  background: var(--system-accent-bg);
  color: var(--system-accent-text);
}

.system-settings-content {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  padding: 10px;
  overflow-y: auto;
}

@media (max-width: 960px) {
  .system-settings-shell {
    grid-template-columns: 1fr;
    height: auto;
    overflow: visible;
  }

  .system-settings-shell__toggle {
    display: none;
  }

  .system-settings-content {
    overflow-y: visible;
  }
}
</style>
