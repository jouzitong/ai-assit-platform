<script setup>
import ModuleView from '../../settings/chart-showcase/views/index.vue'
import '../../settings/chart-showcase/styles/index.scss'
import { useChartShowcasePage } from '../../settings/chart-showcase/service'

const page = useChartShowcasePage()
</script>

<template>
  <ModuleView v-bind="page" />
</template>
