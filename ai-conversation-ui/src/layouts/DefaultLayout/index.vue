<template>
  <div class="default-layout">
    <aside class="default-layout__sidebar">
      <h1>AI Console</h1>
      <nav>
        <RouterLink to="/auth/login">Login</RouterLink>
        <RouterLink to="/system/users">Users</RouterLink>
        <RouterLink to="/">AI Chat</RouterLink>
        <RouterLink to="/data/query">Data Query</RouterLink>
        <RouterLink to="/test/list">Test List</RouterLink>
      </nav>
    </aside>
    <main class="default-layout__content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.default-layout {
  display: grid;
  grid-template-columns: 240px 1fr;
  min-height: 100vh;
}

.default-layout__sidebar {
  padding: 24px;
  background: #0f172a;
  color: #e2e8f0;
}

.default-layout__sidebar nav {
  display: grid;
  gap: 12px;
  margin-top: 24px;
}

.default-layout__sidebar a {
  color: inherit;
  text-decoration: none;
}

.default-layout__content {
  padding: 32px;
}
</style>
