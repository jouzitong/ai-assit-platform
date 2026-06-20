<script setup>
import { useKnowledgeManagePage } from '../../service/knowledge/manage'

const {
  detail,
  documentList,
  currentDocumentKey,
  contentText,
  summaryInfo,
  loading,
  errorMessage,
  selectDocument
} = useKnowledgeManagePage()
</script>

<template>
  <div class="knowledge-detail-page">
    <div v-if="errorMessage" class="page-state is-error">
      {{ errorMessage }}
    </div>
    <div v-else-if="loading" class="page-state">
      正在加载文档内容...
    </div>
    <section v-else class="detail-layout">
      <aside class="document-sidebar">
        <div class="document-sidebar__head">
          <p class="eyebrow">Documents</p>
          <strong>{{ documentList.length }} 篇</strong>
        </div>
        <div class="document-list">
          <button
            v-for="item in documentList"
            :key="`${item.kbCode}::${item.documentCode}`"
            type="button"
            class="document-item"
            :class="{ 'is-active': `${item.kbCode}::${item.documentCode}` === currentDocumentKey }"
            @click="selectDocument(item)"
          >
            <strong>{{ item.documentName }}</strong>
            <span>{{ item.kbCode }}</span>
            <em>{{ item.documentCode }}</em>
          </button>
        </div>
      </aside>

      <section class="detail-main">
        <section class="summary-panel">
          <div class="summary-panel__head">
            <p class="eyebrow">文档概览</p>
            <strong>{{ detail?.documentName || '-' }}</strong>
          </div>
          <div class="summary-panel__body">
            <div class="summary-item">
              <span>KB</span>
              <strong>{{ summaryInfo.kbCode }}</strong>
            </div>
            <div class="summary-item">
              <span>文档编码</span>
              <strong>{{ summaryInfo.documentCode }}</strong>
            </div>
            <div class="summary-item">
              <span>类型</span>
              <strong>{{ summaryInfo.documentType }}</strong>
            </div>
            <div class="summary-item">
              <span>业务键</span>
              <strong>{{ summaryInfo.bizKey }}</strong>
            </div>
            <div class="summary-item">
              <span>来源</span>
              <strong>{{ summaryInfo.source }}</strong>
            </div>
            <div class="summary-item">
              <span>状态</span>
              <strong>{{ summaryInfo.status }}</strong>
            </div>
            <div class="summary-item">
              <span>审核</span>
              <strong>{{ summaryInfo.reviewStatus }}</strong>
            </div>
            <div class="summary-item">
              <span>内容大小</span>
              <strong>{{ summaryInfo.contentSize }}</strong>
            </div>
            <div class="summary-item">
              <span>最近生成</span>
              <strong>{{ summaryInfo.lastGeneratedAt }}</strong>
            </div>
          </div>
        </section>

        <section class="content-card">
          <div class="content-card__head">
            <p class="eyebrow">正文</p>
            <span>{{ detail?.contentFormat || '-' }}</span>
          </div>
          <pre class="content-body">{{ contentText }}</pre>
        </section>
      </section>
    </section>
  </div>
</template>

<style scoped>
.knowledge-detail-page {
  display: grid;
  gap: 12px;
}

.detail-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
  min-height: 0;
}

.eyebrow {
  margin: 0;
  color: var(--text-dim);
  font-size: 11px;
}

.document-sidebar,
.summary-panel,
.content-card,
.page-state {
  border: 1px solid var(--stroke);
  border-radius: 14px;
  background: #fff;
}

.document-sidebar {
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.document-sidebar__head {
  padding: 14px 16px;
  border-bottom: 1px solid var(--stroke);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.document-list {
  padding: 10px;
  display: grid;
  gap: 8px;
  overflow: auto;
}

.document-item {
  padding: 12px 14px;
  border: 1px solid var(--stroke);
  border-radius: 12px;
  background: var(--control-bg);
  text-align: left;
  display: grid;
  gap: 4px;
}

.document-item strong,
.document-item span,
.document-item em {
  font-style: normal;
}

.document-item span,
.document-item em {
  color: var(--text-dim);
  font-size: 12px;
}

.document-item.is-active {
  border-color: rgba(37, 99, 235, 0.3);
  background: rgba(37, 99, 235, 0.08);
}

.detail-main {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 16px;
  min-width: 0;
  min-height: 0;
}

.summary-panel {
  padding: 10px 12px;
  display: grid;
  gap: 8px;
}

.summary-panel__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.summary-panel__body {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 14px;
  align-items: center;
}

.summary-item {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.summary-item span {
  color: var(--text-dim);
  font-size: 11px;
}

.summary-item strong,
.summary-panel__head strong {
  line-height: 1.2;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content-card {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
  min-height: 0;
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
  min-height: 0;
  height: 100%;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, Liberation Mono, monospace;
  font-size: 13px;
  line-height: 1.6;
  background: #f8fafc;
  overflow: auto;
}

.page-state {
  padding: 16px;
}

.page-state.is-error {
  color: #b91c1c;
  background: rgba(254, 242, 242, 0.9);
}

@media (max-width: 1000px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }

  .summary-panel__body {
    grid-template-columns: 1fr;
  }
}
</style>
