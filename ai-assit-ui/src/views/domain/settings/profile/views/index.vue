<script setup>
defineProps({
  pageTitle: {
    type: String,
    required: true
  },
  pageDescription: {
    type: String,
    required: true
  }
})
</script>

<template>
  <main class="page module-page">
    <section class="module-card">
      <h1>{{ pageTitle }}</h1>
      <p>{{ pageDescription }}</p>
    </section>
  </main>
</template>
