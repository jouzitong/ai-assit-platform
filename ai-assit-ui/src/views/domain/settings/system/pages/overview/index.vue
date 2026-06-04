<script setup>
import { Coin, Grid, Setting } from '@element-plus/icons-vue'
</script>

<template>
  <div class="content-head">
    <div>
      <p class="eyebrow">总览</p>
      <h2>配置域分布</h2>
    </div>
  </div>

  <div class="overview-grid">
    <article class="overview-card">
      <Setting :size="18" />
      <strong>系统参数</strong>
      <p>全局开关、默认值、运行阈值、平台级配置。</p>
    </article>
    <article class="overview-card">
      <Grid :size="18" />
      <strong>常用组件</strong>
      <p>页面片段、通用表单、可复用配置块。</p>
    </article>
    <article class="overview-card">
      <Coin :size="18" />
      <strong>AI 接入</strong>
      <p>provider、model、credential 三层实体配置。</p>
    </article>
  </div>
</template>
