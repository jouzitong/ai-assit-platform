<script setup lang="ts">
defineOptions({
  name: 'AppJsonTree',
})

const props = withDefaults(
  defineProps<{
    value: unknown
    label?: string
    depth?: number
  }>(),
  {
    label: 'root',
    depth: 0,
  },
)

function isObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function isBranch(value: unknown) {
  return Array.isArray(value) || isObject(value)
}

function entries(value: unknown) {
  if (Array.isArray(value)) {
    return value.map((item, index) => [String(index), item] as const)
  }
  if (isObject(value)) {
    return Object.entries(value)
  }
  return []
}

function branchSize(value: unknown) {
  if (Array.isArray(value)) {
    return value.length
  }
  if (isObject(value)) {
    return Object.keys(value).length
  }
  return 0
}

function branchType(value: unknown) {
  return Array.isArray(value) ? 'array' : 'object'
}

function formatPrimitive(value: unknown) {
  if (typeof value === 'string') {
    return JSON.stringify(value)
  }
  if (value == null) {
    return String(value)
  }
  return String(value)
}
</script>

<template>
  <div class="app-json-tree" :style="{ '--tree-depth': props.depth }">
    <details v-if="isBranch(value)" class="app-json-tree__branch">
      <summary class="app-json-tree__summary">
        <span class="app-json-tree__key">{{ label }}</span>
        <span class="app-json-tree__meta">{{ branchType(value) }} · {{ branchSize(value) }}</span>
      </summary>
      <div class="app-json-tree__children">
        <AppJsonTree
          v-for="[childKey, childValue] in entries(value)"
          :key="childKey"
          :label="childKey"
          :value="childValue"
          :depth="depth + 1"
        />
      </div>
    </details>

    <div v-else class="app-json-tree__leaf">
      <span class="app-json-tree__key">{{ label }}</span>
      <span class="app-json-tree__value">{{ formatPrimitive(value) }}</span>
    </div>
  </div>
</template>

<style scoped>
.app-json-tree {
  min-width: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
  line-height: 1.6;
}

.app-json-tree__branch,
.app-json-tree__leaf {
  margin-left: calc(var(--tree-depth) * 10px);
}

.app-json-tree__summary,
.app-json-tree__leaf {
  min-width: 0;
  padding: 3px 0;
}

.app-json-tree__summary {
  cursor: pointer;
  user-select: none;
}

.app-json-tree__summary:hover,
.app-json-tree__leaf:hover {
  background: var(--el-fill-color-lighter);
}

.app-json-tree__key {
  margin-right: 8px;
  color: var(--el-color-primary);
  font-weight: 600;
}

.app-json-tree__meta {
  color: var(--el-text-color-secondary);
}

.app-json-tree__value {
  color: var(--el-text-color-regular);
  word-break: break-word;
}

.app-json-tree__children {
  min-width: 0;
  padding-left: 10px;
  border-left: 1px solid var(--el-border-color-lighter);
}
</style>
