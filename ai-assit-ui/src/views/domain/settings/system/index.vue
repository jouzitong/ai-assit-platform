<script setup>
import ModuleView from './views/index.vue'
import './styles/index.css'
import { useSystemPage } from './service'

const page = useSystemPage()
</script>

<template>
  <ModuleView v-bind="page" />
</template>
