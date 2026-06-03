<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SidebarNav from './SidebarNav.vue'
import ChatWidget from './ChatWidget.vue'

const route = useRoute()
const showShell = computed(() => !route.meta?.plainLayout)
</script>

<template>
  <div v-if="showShell" class="app-shell">
    <SidebarNav />
    <section class="content">
      <RouterView />
    </section>
    <ChatWidget />
  </div>
  <RouterView v-else />
</template>
