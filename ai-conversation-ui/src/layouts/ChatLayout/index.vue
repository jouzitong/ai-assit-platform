<template>
  <div class="chat-layout">
    <RouterView />
  </div>
</template>

<style scoped>
.chat-layout {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(148, 163, 184, 0.18), transparent 28%),
    radial-gradient(circle at bottom right, rgba(59, 130, 246, 0.08), transparent 24%),
    linear-gradient(180deg, #fbfbfd 0%, #f1f5f9 100%);
}
</style>
