<script setup>
import { computed, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import SidebarNav from './SidebarNav.vue'
import ChatWidget from './ChatWidget.vue'

const route = useRoute()
const showShell = computed(() => !route.meta?.plainLayout)

function setShellScrollLock(locked) {
  document.documentElement.classList.toggle('layout-lock-scroll', locked)
  document.body.classList.toggle('layout-lock-scroll', locked)
}

watch(
  showShell,
  (locked) => {
    setShellScrollLock(locked)
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  setShellScrollLock(false)
})
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
