<script setup>
import { ArrowLeft, RefreshRight } from '@element-plus/icons-vue'
import { useKnowledgeManagePage } from '../../service/knowledge/manage'

const {
  detail,
  contentText,
  metaItems,
  loading,
  errorMessage,
  goBack,
  refreshPage
} = useKnowledgeManagePage()
</script>

<template>
  <div class="knowledge-detail-page">
    <section class="detail-head">
      <div class="detail-head__left">
        <button type="button" class="toolbar-btn secondary" @click="goBack">
          <ArrowLeft :size="16" />
          返回列表
        </button>
        <div class="detail-copy">
          <p class="eyebrow">文档内容</p>
          <h2>{{ detail?.documentName || detail?.documentCode || '知识库文档' }}</h2>
          <p class="section-desc">{{ detail?.kbCode || '-' }} / {{ detail?.documentCode || '-' }}</p>
        </div>
      </div>
      <div class="detail-head__right">
        <button type="button" class="toolbar-btn secondary" @click="refreshPage">
          <RefreshRight :size="16" />
          刷新
        </button>
      </div>
    </section>

    <div v-if="errorMessage" class="page-state is-error">
      {{ errorMessage }}
    </div>
    <div v-else-if="loading" class="page-state">
      正在加载文档内容...
    </div>
    <template v-else>
      <section class="meta-grid">
        <article v-for="item in metaItems" :key="item.label" class="meta-card">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </section>

      <section class="content-card">
        <div class="content-card__head">
          <p class="eyebrow">正文</p>
          <span>{{ detail?.contentFormat || '-' }}</span>
        </div>
        <pre class="content-body">{{ contentText }}</pre>
      </section>
    </template>
  </div>
</template>

<style scoped>
.knowledge-detail-page {
  display: grid;
  gap: 18px;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.detail-head__left {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.detail-copy h2 {
  margin: 0 0 6px;
}

.section-desc,
.eyebrow {
  margin: 0;
}

.eyebrow {
  color: var(--text-dim);
  font-size: 12px;
}

.section-desc {
  color: var(--text-dim);
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.meta-card,
.content-card,
.page-state {
  border: 1px solid var(--stroke);
  border-radius: 14px;
  background: #fff;
}

.meta-card {
  padding: 14px 16px;
  display: grid;
  gap: 8px;
}

.meta-card span {
  color: var(--text-dim);
  font-size: 12px;
}

.content-card {
  overflow: hidden;
}

.content-card__head {
  padding: 14px 16px;
  border-bottom: 1px solid var(--stroke);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.content-body {
  margin: 0;
  padding: 18px 16px;
  min-height: 420px;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, Liberation Mono, monospace;
  font-size: 13px;
  line-height: 1.6;
  background: #f8fafc;
}

.page-state {
  padding: 16px;
}

.page-state.is-error {
  color: #b91c1c;
  background: rgba(254, 242, 242, 0.9);
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border: 1px solid var(--stroke);
  border-radius: 10px;
  background: var(--control-bg);
}
</style>
