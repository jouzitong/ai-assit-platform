<script setup>
import HomeOverviewView from './views/index.vue'
import './styles/index.scss'
import { useHomeOverview } from './service'

const page = useHomeOverview()
</script>

<template>
  <HomeOverviewView v-bind="page" />
</template>
