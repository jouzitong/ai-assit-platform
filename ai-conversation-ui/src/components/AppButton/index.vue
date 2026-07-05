<script setup lang="ts">
defineProps<{
  label?: string
}>()
</script>

<template>
  <el-button>
    <slot>{{ label }}</slot>
  </el-button>
</template>
