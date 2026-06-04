<script setup>
import HomeOverviewView from './views/index.vue'
import './styles/index.css'
import { useHomeOverview } from './service'

const page = useHomeOverview()
</script>

<template>
  <HomeOverviewView v-bind="page" />
</template>
