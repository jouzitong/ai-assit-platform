<script setup>
import ModuleView from './views/index.vue'
import './styles/index.css'
import { useStaticModulePage } from './service'

const page = useStaticModulePage()
</script>

<template>
  <ModuleView v-bind="page" />
</template>
