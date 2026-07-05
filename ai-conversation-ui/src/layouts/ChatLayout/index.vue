<template>
  <div class="chat-layout">
    <section class="chat-layout__panel">
      <RouterView />
    </section>
  </div>
</template>

<style scoped>
.chat-layout {
  min-height: 100vh;
  padding: 24px;
  background:
    radial-gradient(circle at top, rgba(45, 212, 191, 0.12), transparent 38%),
    linear-gradient(180deg, #f8fafc 0%, #e2e8f0 100%);
}

.chat-layout__panel {
  max-width: 1200px;
  margin: 0 auto;
}
</style>
