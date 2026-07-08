<script setup>
import ModuleView from './views/index.vue'
import './styles/index.scss'
import { useChartShowcasePage } from './service'

const page = useChartShowcasePage()
</script>

<template>
  <ModuleView v-bind="page" />
</template>
