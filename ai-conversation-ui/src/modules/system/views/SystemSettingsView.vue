<script setup lang="ts">
import { ArrowLeftBold, ArrowRightBold, Coin, Connection, Cpu, DataAnalysis, Grid, Setting, Share, Tickets, UserFilled } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AgentAssistantHost from '../../ai-assistant/components/AgentAssistantHost.vue'
import SystemSettingsSidebar from '../components/SystemSettingsSidebar.vue'
import AiPlatformSection from '../components/sections/AiPlatformSection.vue'
import AiModelManagementSection from '../components/sections/AiModelManagementSection.vue'
import ComponentManageSection from '../components/sections/ComponentManageSection.vue'
import DataSourceSection from '../components/sections/DataSourceSection.vue'
import DataSourceTableSection from '../components/sections/DataSourceTableSection.vue'
import ErrorCodeSection from '../components/sections/ErrorCodeSection.vue'
import KbDocumentManageSection from '../components/sections/KbDocumentManageSection.vue'
import MetadataConfigSection from '../components/sections/MetadataConfigSection.vue'
import SystemParamsSection from '../components/sections/SystemParamsSection.vue'
import UserManagementSection from '../components/sections/UserManagementSection.vue'
import WorkflowSection from '../components/sections/WorkflowSection.vue'
import VirtualTableManagement from '../virtual-table/index.vue'

type SettingsSection = {
  key: string
  label: string
  icon: unknown
  title: string
  description: string
  component?: unknown
  children?: Array<Pick<SettingsSection, 'key' | 'label' | 'icon' | 'title' | 'description' | 'component'>>
}

const router = useRouter()
const route = useRoute()
const sidebarCollapsed = ref(false)

const sections: SettingsSection[] = [
  {
    key: 'user-management',
    label: '用户管理',
    icon: UserFilled,
    title: '用户管理',
    description: '管理平台用户、角色以及用户与角色的关联关系。',
    component: UserManagementSection,
  },
  {
    key: 'system-params',
    label: '系统参数',
    icon: Setting,
    title: '系统参数',
    description: '集中管理全局运行参数、默认开关和页面级配置。',
    component: SystemParamsSection,
  },
  {
    key: 'data-management',
    label: '数据源',
    icon: Coin,
    title: '数据源管理',
    description: '统一维护物理数据源和跨数据源虚拟模型。',
    children: [
      {
        key: 'data-source',
        label: '数据源配置',
        icon: Connection,
        title: '数据源配置',
        description: '维护数据库连接、授权信息和物理表元数据。',
        component: DataSourceSection,
      },
      {
        key: 'virtual-table',
        label: '虚拟表管理',
        icon: Grid,
        title: '虚拟表管理',
        description: '维护虚拟字段、物理映射、转换规则和表关联。',
        component: VirtualTableManagement,
      },
    ],
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
    description: '将 Application 组件配置为可检索、可同步的系统数字资产。',
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
    children: [
      {
        key: 'ai-platform-model',
        label: '模型管理',
        icon: Cpu,
        title: '模型管理',
        description: '维护模型连接、鉴权信息和启用状态。',
        component: AiPlatformSection,
      },
      {
        key: 'ai-platform-kb',
        label: '知识库管理',
        icon: Cpu,
        title: '知识库管理',
        description: '维护知识库客户端、数据集和文档。',
        component: AiPlatformSection,
      },
    ],
  },
  {
    key: 'workflow',
    label: '智能体配置',
    icon: Share,
    title: '流程配置',
    description: '配置工作流节点、执行链路和流程编排策略。',
    component: WorkflowSection,
  },
]

const routeSection = computed(() => typeof route.params.section === 'string' ? route.params.section : '')
const routeSourceKey = computed(() => typeof route.params.sourceKey === 'string' ? route.params.sourceKey : '')
const navigableSections = computed(() => sections.flatMap(item => item.children?.length ? item.children : [item]))
const isAiPlatformSection = computed(() => routeSection.value === 'ai-platform')
const aiPlatformTab = computed<'model' | 'kb'>(() => {
  if (routeSourceKey.value && routeSourceKey.value !== 'model') {
    return 'kb'
  }
  return route.query.tab === 'kb' ? 'kb' : 'model'
})

const activeSection = computed(() => {
  if (isAiPlatformSection.value) {
    return `ai-platform-${aiPlatformTab.value}`
  }
  return navigableSections.value.some(item => item.key === routeSection.value) ? routeSection.value : navigableSections.value[0].key
})

const hasDataSourceDetail = computed(() => routeSection.value === 'data-source' && routeSourceKey.value.trim().length > 0)
const hasAiPlatformKbDetail = computed(() => isAiPlatformSection.value && !['model', 'kb'].includes(routeSourceKey.value) && routeSourceKey.value.trim().length > 0)
const currentSection = computed(() => navigableSections.value.find(item => item.key === routeSection.value) || navigableSections.value[0])

async function navigateToSection(sectionKey: string) {
  if (activeSection.value === sectionKey) {
    return
  }
  if (sectionKey === 'ai-platform-model') {
    await router.push('/settings/system/ai-platform/model')
    return
  }
  if (sectionKey === 'ai-platform-kb') {
    await router.push('/settings/system/ai-platform/kb')
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
  if (!isAiPlatformSection.value && !navigableSections.value.some(item => item.key === routeSection.value)) {
    void router.replace(`/settings/system/${navigableSections.value[0].key}`)
    return
  }

  if (isAiPlatformSection.value && !routeSourceKey.value) {
    const legacyTab = route.query.tab === 'kb' ? 'kb' : 'model'
    const { tab: _tab, ...query } = route.query
    void router.replace({
      path: `/settings/system/ai-platform/${legacyTab}`,
      query,
    })
  }
})
</script>

<template>
  <div
    class="system-settings-shell"
    :class="{ 'system-settings-shell--collapsed': sidebarCollapsed }"
    :style="{ '--system-sidebar-width': sidebarCollapsed ? '0px' : '236px' }"
  >
    <SystemSettingsSidebar
      v-if="!sidebarCollapsed"
      id="system-settings-sidebar"
      :sections="sections"
      :active-section="activeSection"
      @navigate-home="navigateHome"
      @select-section="navigateToSection"
    />

    <button
      class="system-settings-shell__toggle"
      type="button"
      aria-controls="system-settings-sidebar"
      :aria-expanded="!sidebarCollapsed"
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
      <DataSourceTableSection v-if="hasDataSourceDetail" />
      <KbDocumentManageSection v-else-if="hasAiPlatformKbDetail" />
      <AiModelManagementSection v-else-if="isAiPlatformSection && aiPlatformTab === 'model'" />
      <AiPlatformSection v-else-if="isAiPlatformSection" :active-tab="aiPlatformTab" />
      <component v-else :is="currentSection.component" />
    </main>

    <AgentAssistantHost />
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

.system-settings-shell--collapsed {
  grid-template-columns: minmax(0, 1fr);
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

.system-settings-shell--collapsed .system-settings-shell__toggle {
  left: 12px;
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
    right: 12px;
    left: auto;
  }

  .system-settings-content {
    overflow-y: visible;
  }
}
</style>
