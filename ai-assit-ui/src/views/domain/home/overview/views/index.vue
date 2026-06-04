<script setup>
defineProps({
  metricCards: {
    type: Array,
    required: true
  },
  todoList: {
    type: Array,
    required: true
  },
  alertList: {
    type: Array,
    required: true
  }
})
</script>

<template>
  <main class="page">
    <section class="hero">
      <p class="tag">EMP Dashboard</p>
      <h1>运营总览</h1>
      <p class="desc">首页聚合数据库核心指标、待办提醒与风险动态，聊天助手通过右下角机器人入口使用。</p>
    </section>

    <section class="stats-grid">
      <article v-for="card in metricCards" :key="card.title" class="metric-card">
        <p>{{ card.title }}</p>
        <h3>{{ card.value }}</h3>
        <span>{{ card.note }}</span>
      </article>
    </section>

    <section class="grid two-col">
      <article class="panel-card">
        <h2>待办事项</h2>
        <ul class="todo-list">
          <li v-for="item in todoList" :key="item">{{ item }}</li>
        </ul>
      </article>

      <article class="panel-card">
        <h2>数据监控提醒</h2>
        <ul class="todo-list">
          <li v-for="item in alertList" :key="item">{{ item }}</li>
        </ul>
      </article>
    </section>
  </main>
</template>
