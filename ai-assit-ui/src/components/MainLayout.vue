<script setup>
import SidebarNav from './SidebarNav.vue'
import ChatWidget from './ChatWidget.vue'
</script>

<template>
  <div class="app-shell">
    <SidebarNav />
    <section class="content">
      <RouterView />
    </section>
    <ChatWidget />
  </div>
</template>
