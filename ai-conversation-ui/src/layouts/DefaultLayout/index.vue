<template>
  <div class="default-layout">
    <main class="default-layout__content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.default-layout {
  min-height: 100vh;
}

.default-layout__content {
  padding: 32px;
}
</style>
