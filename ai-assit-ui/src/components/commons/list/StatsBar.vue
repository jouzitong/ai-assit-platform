<script setup>
import { computed } from 'vue'

const props = defineProps({
  minWidth: {
    type: Number,
    default: 150
  },
  items: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['select'])

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(auto-fit, minmax(${Math.max(80, props.minWidth)}px, 1fr))`
}))

function labelStyle(item) {
  const config = item?.class_config
  return config?.labelColor || config?.labelSize ? {
    color: config.labelColor,
    fontSize: typeof config.labelSize === 'number' ? `${config.labelSize}px` : config.labelSize
  } : null
}

function valueStyle(item) {
  const config = item?.class_config
  return config?.valueColor || config?.valueSize ? {
    color: config.valueColor,
    fontSize: typeof config.valueSize === 'number' ? `${config.valueSize}px` : config.valueSize
  } : null
}
</script>

<template>
  <div class="stats" :style="gridStyle">
    <div
      v-for="(item, index) in items"
      :key="item?.key || index"
      class="stat-card"
      @click="emit('select', item)"
    >
      <h4 :style="labelStyle(item)">
        {{ item.label }}
      </h4>
      <p :style="valueStyle(item)">
        {{ item.value }}
      </p>
    </div>
  </div>
</template>

<style scoped>
.stats {
  display: grid;
  gap: 14px;
  margin-bottom: 12px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 10px 12px;
  border-radius: 14px;
  background: var(--bg-elev);
  border: 1px solid var(--stroke);
  cursor: pointer;
}

.stat-card h4 {
  margin: 0 0 4px;
  font-size: 11px;
  color: var(--text-dim);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.stat-card p {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
</style>
