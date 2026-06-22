<script setup>
</script>

<template>
  <div class="system-content">
    <RouterView />
  </div>
</template>

<style>
.system-content {
  --sys-shell-padding: 0px;
  --sys-surface: #ffffff;
  --sys-surface-soft: #fbfdff;
  --sys-border: rgba(226, 232, 240, 0.9);
  --sys-shadow-soft: 0 8px 20px rgba(15, 23, 42, 0.04);
  --sys-radius-lg: 18px;
  --sys-radius-md: 14px;
  min-width: 0;
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  display: grid;
  grid-template-rows: minmax(0, 1fr);
  gap: 0;
  padding: var(--sys-shell-padding);
  border: 1px solid rgba(226, 232, 240, 0.78);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(250, 251, 253, 0.98));
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.03);
  overflow: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.system-content > * {
  min-width: 0;
  min-height: 0;
}

.content-head,
.workspace-card,
.table-panel,
.placeholder-panel,
.overview-card,
.editor-card {
  border-radius: var(--sys-radius-lg);
  border: 1px solid var(--sys-border);
  background: var(--sys-surface);
  box-shadow: var(--sys-shadow-soft);
}

.content-head {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: end;
  padding: 18px 20px;
}

.content-head h1,
.content-head h2 {
  margin: 0;
  color: #0f172a;
}

.content-head h1 {
  font-size: 30px;
  line-height: 1.05;
  letter-spacing: -0.03em;
}

.content-head h2 {
  font-size: 24px;
  line-height: 1.15;
  letter-spacing: -0.02em;
}

.eyebrow {
  margin: 0 0 8px;
  color: #2563eb;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  font-size: 11px;
  font-weight: 800;
}

.section-desc {
  margin: 10px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
  max-width: 680px;
}

.content-head.compact {
  align-items: center;
}

.workspace-card,
.table-panel,
.editor-card {
  padding: 16px;
}

.hero-stats {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.hero-stat {
  min-width: 104px;
  padding: 14px 16px;
  border-radius: var(--sys-radius-lg);
  background: var(--sys-surface);
  border: 1px solid var(--sys-border);
  display: grid;
  gap: 4px;
}

.hero-stat strong {
  font-size: 22px;
  letter-spacing: -0.02em;
  color: #0f172a;
}

.hero-stat span {
  color: #64748b;
  font-size: 13px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.overview-card,
.placeholder-panel,
.table-card,
.editor-card {
  border: 1px solid var(--sys-border);
  border-radius: var(--sys-radius-lg);
  background: var(--sys-surface);
  box-shadow: var(--sys-shadow-soft);
  backdrop-filter: none;
}

.overview-card {
  padding: 16px;
  display: grid;
  gap: 10px;
  color: #0f172a;
}

.placeholder-panel {
  padding: 20px;
  color: #334155;
  line-height: 1.6;
  background: var(--sys-surface-soft);
}

.ai-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.ai-tabs {
  display: grid;
  gap: 10px;
  align-self: start;
}

.ai-tab {
  border: 1px solid rgba(226, 232, 240, 0.95);
  padding: 14px;
  display: grid;
  grid-template-columns: 36px 1fr;
  gap: 12px;
  align-items: center;
  cursor: pointer;
  text-align: left;
  border-radius: 18px;
  background: #fff;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.ai-tab.active {
  border-color: rgba(37, 99, 235, 0.22);
  background: #fff;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.05);
}

.ai-tab span {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  color: #1d4ed8;
  font-weight: 700;
}

.ai-tab div {
  display: grid;
  gap: 4px;
}

.ai-tab small {
  color: #64748b;
}

.ai-workbench {
  display: grid;
  gap: 16px;
}

.toolbar {
  display: flex;
  gap: 12px;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
}

.search-box {
  min-width: 280px;
  flex: 1;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  border-radius: var(--sys-radius-md);
  background: var(--sys-surface);
  border: 1px solid var(--sys-border);
}

.search-box input {
  width: 100%;
  border: 0;
  outline: none;
  background: transparent;
  font: inherit;
  color: var(--app-text);
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 14px;
  padding: 11px 16px;
  font: inherit;
  cursor: pointer;
}

.btn.primary {
  color: #eff6ff;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.16);
}

.btn.secondary {
  background: var(--sys-surface);
  color: #0f172a;
  border: 1px solid var(--sys-border);
}

.table-card,
.editor-card {
  padding: 16px;
}

.config-table {
  width: 100%;
  border-collapse: collapse;
}

.config-table th,
.config-table td {
  padding: 12px;
  border-bottom: 1px solid var(--sys-border);
  text-align: left;
  vertical-align: top;
}

.config-table th {
  color: #64748b;
  font-size: 13px;
}

.row-actions {
  white-space: nowrap;
}

.icon-btn {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 10px;
  margin-left: 8px;
  background: rgba(148, 163, 184, 0.12);
  cursor: pointer;
}

.icon-btn.danger {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.08);
}

.empty-row {
  padding: 20px 12px;
  text-align: center;
  color: var(--app-text-muted);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.form-field {
  display: grid;
  gap: 8px;
}

.form-field span {
  font-size: 13px;
  color: var(--app-text-soft);
}

.form-field em {
  color: #ef4444;
  font-style: normal;
}

.form-field input,
.form-field textarea,
.form-field select {
  width: 100%;
  border-radius: 14px;
  border: 1px solid var(--app-border);
  background: var(--app-surface-strong);
  padding: 11px 12px;
  font: inherit;
  color: var(--app-text);
}

.form-field textarea {
  resize: vertical;
}

.form-error {
  margin: 14px 0 0;
  color: #dc2626;
}

.editor-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 18px;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--app-accent);
  text-transform: uppercase;
  letter-spacing: 0.2em;
  font-size: 12px;
  font-weight: 700;
}

.section-desc,
.editor-hint {
  margin: 10px 0 0;
  color: var(--app-text-muted);
}

.content-head h2,
.editor-card h3 {
  margin: 0;
}

@media (max-width: 1180px) {
  .overview-grid,
  .ai-layout,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .system-content {
    overflow: visible;
    padding-right: 0;
    --sys-shell-padding: 0px;
  }
}

@media (max-width: 760px) {
  .content-head,
  .toolbar,
  .editor-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .search-box {
    min-width: 0;
    width: 100%;
  }

  .content-head {
    padding: 16px;
  }

  .content-head h1 {
    font-size: 26px;
  }
}
</style>
