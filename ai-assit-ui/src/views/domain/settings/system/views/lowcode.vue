<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import LowcodePage from '../../../../../lowcode/views/LowcodePage.vue'

const route = useRoute()
const pageCode = computed(() => String(route.query.pageCode || route.query.viewId || ''))
</script>

<template>
  <LowcodePage :page-code="pageCode" />
</template>
