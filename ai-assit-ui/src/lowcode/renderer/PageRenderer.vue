<script setup>
import { computed } from 'vue'
import { resolveRenderer } from '../registry/rendererRegistry'

const props = defineProps({
  runtime: {
    type: Object,
    required: true
  }
})

const rendererComponent = computed(() => resolveRenderer(props.runtime.schema.layout.type))
</script>

<template>
  <component :is="rendererComponent" :runtime="runtime" :renderer-key="runtime.schema.layout.type" />
</template>
