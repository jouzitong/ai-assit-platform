<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import CatalogListView from '../components/CatalogListView.vue'
import {
  deleteAgent,
  listAgentEntries,
  listAgentOptions,
  listAgents,
  listHomeAvailableAgents,
  publishAgent,
  updateAgentEntry,
  validateAgent,
} from '../api/agents'
import type { AvailableAgent } from '../types'

const availableAgents = ref<AvailableAgent[]>([])
const selectedAgentCode = ref('')
const versionStrategy = ref<'LATEST_PUBLISHED' | 'PINNED'>('LATEST_PUBLISHED')
const pinnedVersion = ref<number | undefined>()
const entryLoading = ref(false)
const entrySaving = ref(false)
const entryError = ref('')

const selectedAgent = computed(() => availableAgents.value.find(agent => agent.code === selectedAgentCode.value))

async function loadHomeEntry() {
  entryLoading.value = true
  entryError.value = ''
  try {
    const [entries, boundAgents, catalogResult] = await Promise.all([
      listAgentEntries(),
      listHomeAvailableAgents(),
      listAgentOptions(),
    ])
    const catalogAgents = (Array.isArray(catalogResult) ? catalogResult : catalogResult.list || [])
      .filter(agent => agent.enabled !== false && Number(agent.currentPublishedVersion || 0) > 0)
      .map(agent => ({
        code: agent.code,
        name: agent.name,
        description: agent.description,
        version: agent.currentPublishedVersion,
      }))
    const merged = new Map<string, AvailableAgent>()
    for (const agent of [...(Array.isArray(boundAgents) ? boundAgents : []), ...catalogAgents]) {
      if (agent.code && !merged.has(agent.code)) merged.set(agent.code, agent)
    }
    availableAgents.value = [...merged.values()]
    const binding = (Array.isArray(entries) ? entries : []).find(item => item.entryCode === 'HOME_CHAT')
    selectedAgentCode.value = binding?.agentCode || availableAgents.value[0]?.code || ''
    versionStrategy.value = binding?.versionStrategy === 'PINNED' ? 'PINNED' : 'LATEST_PUBLISHED'
    pinnedVersion.value = binding?.pinnedVersion || selectedAgent.value?.version
  }
  catch (error) {
    entryError.value = error instanceof Error ? error.message : 'HOME_CHAT 入口加载失败'
  }
  finally {
    entryLoading.value = false
  }
}

async function saveHomeEntry() {
  if (!selectedAgentCode.value) {
    ElMessage.warning('请选择一个已发布 Agent')
    return
  }
  if (versionStrategy.value === 'PINNED' && !pinnedVersion.value) {
    ElMessage.warning('固定版本策略必须填写版本号')
    return
  }
  entrySaving.value = true
  try {
    await updateAgentEntry('HOME_CHAT', {
      entryCode: 'HOME_CHAT',
      agentCode: selectedAgentCode.value,
      versionStrategy: versionStrategy.value,
      pinnedVersion: versionStrategy.value === 'PINNED' ? pinnedVersion.value : null,
      enabled: true,
    })
    ElMessage.success('首页 Agent 入口已更新')
    await loadHomeEntry()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '入口绑定保存失败')
  }
  finally {
    entrySaving.value = false
  }
}

onMounted(loadHomeEntry)

watch([selectedAgentCode, versionStrategy], ([, strategy]) => {
  if (entryLoading.value) return
  if (strategy === 'PINNED') pinnedVersion.value = selectedAgent.value?.version
}, { flush: 'sync' })
</script>

<template>
  <CatalogListView
    title="Agent 管理"
    description="管理跨 Python / TypeScript Runtime 的中立 AgentManifest、版本与发布状态。"
    resource-label="Agent"
    route-base="/settings/system/agents"
    :list-request="listAgents"
    :delete-request="deleteAgent"
    :validate-request="validateAgent"
    :publish-request="publishAgent"
  >
    <template #before-list>
      <section v-loading="entryLoading" class="home-agent-entry">
        <div class="home-agent-entry__copy">
          <strong>首页 Agent 入口</strong>
          <span>聊天未显式指定 target 时，由 HOME_CHAT 绑定选择已发布根 Agent。</span>
        </div>
        <el-select
          v-model="selectedAgentCode"
          filterable
          placeholder="选择已发布 Agent"
          :disabled="entryLoading || entrySaving"
        >
          <el-option
            v-for="agent in availableAgents"
            :key="agent.code"
            :label="agent.name || agent.code"
            :value="agent.code"
          >
            <span>{{ agent.name || agent.code }}</span>
            <small class="home-agent-entry__option-code">{{ agent.code }}<template v-if="agent.version"> · v{{ agent.version }}</template></small>
          </el-option>
        </el-select>
        <el-select v-model="versionStrategy" :disabled="entryLoading || entrySaving">
          <el-option label="跟随最新已发布版本" value="LATEST_PUBLISHED" />
          <el-option label="固定版本" value="PINNED" />
        </el-select>
        <el-input-number
          v-if="versionStrategy === 'PINNED'"
          v-model="pinnedVersion"
          :min="1"
          :disabled="entryLoading || entrySaving"
          aria-label="固定 Agent 版本"
        />
        <el-button type="primary" :loading="entrySaving" :disabled="entryLoading" @click="saveHomeEntry">保存入口</el-button>
        <span v-if="entryError" class="home-agent-entry__error">{{ entryError }}</span>
      </section>
    </template>
  </CatalogListView>
</template>

<style scoped>
.home-agent-entry {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(200px, 280px) minmax(170px, 220px) auto auto;
  gap: var(--app-space-3);
  align-items: center;
  padding: var(--app-space-4);
  border: 1px solid var(--app-accent-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-accent-bg);
}

.home-agent-entry__copy {
  display: grid;
  gap: 4px;
}

.home-agent-entry__copy strong { color: var(--system-text); }
.home-agent-entry__copy span,
.home-agent-entry__option-code { color: var(--system-text-muted); font-size: 12px; }
.home-agent-entry__option-code { float: right; margin-left: var(--app-space-3); }
.home-agent-entry__error { grid-column: 1 / -1; color: var(--el-color-danger); font-size: 12px; }

@container (max-width: 960px) {
  .home-agent-entry { grid-template-columns: 1fr 1fr; }
  .home-agent-entry__copy { grid-column: 1 / -1; }
}

@container (max-width: 620px) {
  .home-agent-entry { grid-template-columns: 1fr; }
  .home-agent-entry__copy { grid-column: auto; }
}
</style>
