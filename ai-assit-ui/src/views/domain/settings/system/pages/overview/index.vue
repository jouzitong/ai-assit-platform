<script setup>
import {
  ArrowRight,
  Coin,
  Collection,
  DataLine,
  Grid,
  Location,
  Monitor,
  Notification,
  Opportunity,
  Reading,
  Tickets,
  TrendCharts
} from '@element-plus/icons-vue'

const notices = [
  {
    date: '2026.06.04',
    title: '配置中心首页升级为门户化布局',
    desc: '把常用入口、推荐能力、教程和案例统一收口到总览页。'
  },
  {
    date: '2026.06.01',
    title: '系统参数支持更细粒度的分组展示',
    desc: '参数、组件和 AI 接入入口继续保持独立，首页只负责分发和导览。'
  },
  {
    date: '2026.05.28',
    title: 'AI 接入页完成基础台账结构',
    desc: 'provider、model、credential 三层结构已可独立维护。'
  },
  {
    date: '2026.05.22',
    title: '常用组件页补齐模板骨架',
    desc: '后续可沉淀表单片段、表格列定义和页面区块。'
  }
]

const quickEntries = [
  {
    key: 'params',
    label: '系统参数',
    summary: '全局开关、默认值、运行阈值',
    icon: Grid,
    to: '/settings/system/params'
  },
  {
    key: 'components',
    label: '常用组件',
    summary: '页面片段、模板和复用组件',
    icon: Collection,
    to: '/settings/system/components'
  },
  {
    key: 'ai',
    label: 'AI 接入',
    summary: 'provider、model、credential',
    icon: Monitor,
    to: '/settings/system/ai'
  },
  {
    key: 'overview',
    label: '系统总览',
    summary: '配置域分布和当前状态',
    icon: TrendCharts,
    to: '/settings/system/overview'
  }
]

const savingsPlans = [
  {
    title: '基础接入包',
    tag: '入门',
    price: '免费',
    term: '长期有效',
    desc: '适合单人试用、快速联调和基础验证。'
  },
  {
    title: '团队协作包',
    tag: '推荐',
    price: '按需',
    term: '灵活开通',
    desc: '适合多角色协同、统一参数和共享模板。'
  },
  {
    title: '企业治理包',
    tag: '高阶',
    price: '定制',
    term: '按组织规模',
    desc: '适合有审计、权限和统一治理诉求的团队。'
  },
  {
    title: 'AI 扩展包',
    tag: '热门',
    price: '可选',
    term: '与 AI 接入联动',
    desc: '适合继续扩展 provider、model 和能力模板。'
  }
]

const models = [
  {
    name: 'Qwen 文本接入',
    category: '文本生成',
    price: '按调用计费',
    desc: '用于对话、总结、结构化抽取和通用文本处理。'
  },
  {
    name: 'DeepSeek 推理接入',
    category: '推理增强',
    price: '按调用计费',
    desc: '用于复杂推理、代码生成和长链路任务。'
  },
  {
    name: '多模态能力包',
    category: '图像 / 音视频',
    price: '按资源计费',
    desc: '用于图像理解、音视频识别和跨模态交互。'
  },
  {
    name: '组件模板库',
    category: '页面片段',
    price: '即开即用',
    desc: '用于沉淀常用表单、列表、详情和配置模板。'
  },
  {
    name: '配置治理包',
    category: '系统治理',
    price: '可追踪',
    desc: '用于参数审计、变更记录和访问范围控制。'
  }
]

const appHighlights = [
  {
    title: '系统总览',
    desc: '配置域分布与入口分发',
    tag: '导航',
    to: '/settings/system/overview'
  },
  {
    title: '系统参数',
    desc: '管理全局开关和默认值',
    tag: '参数',
    to: '/settings/system/params'
  },
  {
    title: '常用组件',
    desc: '沉淀表单片段和模板块',
    tag: '组件',
    to: '/settings/system/components'
  },
  {
    title: 'AI 接入',
    desc: '维护 provider、model 和 credential',
    tag: 'AI',
    to: '/settings/system/ai'
  }
]

const tutorials = [
  {
    title: '新手通识课：快速理解配置中心分层',
    category: '快速入门',
    date: '更新时间 2026.06.03'
  },
  {
    title: '如何把系统参数拆成可维护的分组',
    category: '参数治理',
    date: '更新时间 2026.05.30'
  },
  {
    title: '如何构建可复用的常用组件模板',
    category: '组件复用',
    date: '更新时间 2026.05.27'
  },
  {
    title: 'AI 接入台账的最佳实践',
    category: 'AI 接入',
    date: '更新时间 2026.05.24'
  }
]

const cases = [
  {
    title: '研发平台统一配置',
    desc: '将分散在多个页面的开关和默认值收拢到系统参数。'
  },
  {
    title: '多团队组件复用',
    desc: '通过常用组件页沉淀公共表单和页面区块。'
  },
  {
    title: 'AI 接入治理',
    desc: '用 provider、model、credential 三层结构做统一接入管理。'
  },
  {
    title: '配置变更审计',
    desc: '在系统治理过程中保留关键变更轨迹和责任边界。'
  }
]
</script>

<template>
  <div class="console-home">
    <section class="hero-grid">
      <article class="hero-banner">
        <p class="eyebrow">系统配置中心</p>
        <h2>像百炼控制台一样组织配置入口</h2>
        <p class="hero-desc">
          这页按门户首页的方式排布信息：上方看通知，中间找常用能力，下面看推荐能力、教程和案例。
          侧边栏保持你现在的四个入口不变。
        </p>

        <div class="hero-actions">
          <RouterLink to="/settings/system/ai" class="btn primary">
            进入 AI 接入
            <ArrowRight :size="16" />
          </RouterLink>
          <RouterLink to="/settings/system/params" class="btn secondary">
            查看系统参数
          </RouterLink>
        </div>

        <div class="hero-stats">
          <div class="stat-pill">
            <strong>4</strong>
            <span>侧边栏入口</span>
          </div>
          <div class="stat-pill">
            <strong>3</strong>
            <span>核心配置域</span>
          </div>
          <div class="stat-pill">
            <strong>1</strong>
            <span>门户首页</span>
          </div>
        </div>
      </article>

      <div class="hero-stack">
        <article class="promo-card promo-card-accent">
          <div class="promo-top">
            <span class="promo-badge">推荐</span>
            <Notification :size="18" />
          </div>
          <h3>最新通知</h3>
          <p>把配置变更、功能上线和治理动作放在最显眼的位置。</p>
        </article>

        <article class="promo-card promo-card-soft">
          <div class="promo-top">
            <span class="promo-badge muted">快捷</span>
            <TrendCharts :size="18" />
          </div>
          <h3>常用功能</h3>
          <p>系统参数、常用组件和 AI 接入都能直接跳转。</p>
        </article>
      </div>
    </section>

    <section class="section-block">
      <div class="section-head">
        <div>
          <p class="eyebrow">最新通知</p>
          <h3>通知中心</h3>
        </div>
        <RouterLink to="/settings/system/overview" class="section-link">
          查看全部
          <ArrowRight :size="14" />
        </RouterLink>
      </div>

      <div class="notice-list">
        <article v-for="notice in notices" :key="notice.title" class="notice-card">
          <span class="notice-date">{{ notice.date }}</span>
          <strong>{{ notice.title }}</strong>
          <p>{{ notice.desc }}</p>
        </article>
      </div>
    </section>

    <section class="section-block">
      <div class="section-head">
        <div>
          <p class="eyebrow">常用功能</p>
          <h3>快速入口</h3>
        </div>
        <span class="section-meta">保留你现在的侧边栏 item 结构</span>
      </div>

      <div class="quick-grid">
        <RouterLink
          v-for="entry in quickEntries"
          :key="entry.key"
          :to="entry.to"
          class="quick-card"
        >
          <span class="quick-icon">
            <component :is="entry.icon" :size="18" />
          </span>
          <div>
            <strong>{{ entry.label }}</strong>
            <p>{{ entry.summary }}</p>
          </div>
        </RouterLink>
      </div>
    </section>

    <section class="section-block">
      <div class="section-head">
        <div>
          <p class="eyebrow">省钱计划</p>
          <h3>能力方案</h3>
        </div>
        <span class="section-meta">视觉上借用百炼首页的套餐区块</span>
      </div>

      <div class="plan-grid">
        <article v-for="plan in savingsPlans" :key="plan.title" class="plan-card">
          <div class="plan-head">
            <span class="plan-tag">{{ plan.tag }}</span>
            <strong>{{ plan.title }}</strong>
          </div>
          <p>{{ plan.desc }}</p>
          <div class="plan-footer">
            <div>
              <span class="plan-label">适用方式</span>
              <strong>{{ plan.term }}</strong>
            </div>
            <div class="plan-price">{{ plan.price }}</div>
          </div>
          <button class="plan-action" type="button">查看详情</button>
        </article>
      </div>
    </section>

    <section class="section-block">
      <div class="section-head">
        <div>
          <p class="eyebrow">推荐模型</p>
          <h3>能力广场</h3>
        </div>
        <span class="section-meta">这里用你的配置域替代百炼的模型商品卡</span>
      </div>

      <div class="model-grid">
        <article v-for="model in models" :key="model.name" class="model-card">
          <div class="model-top">
            <span class="model-icon">
              <Coin :size="18" />
            </span>
            <span class="model-category">{{ model.category }}</span>
          </div>
          <strong>{{ model.name }}</strong>
          <p>{{ model.desc }}</p>
          <div class="model-price">{{ model.price }}</div>
        </article>
      </div>
    </section>

    <section class="section-block">
      <div class="section-head">
        <div>
          <p class="eyebrow">推荐应用</p>
          <h3>应用广场</h3>
        </div>
        <span class="section-meta">用路由入口模拟应用市场卡片</span>
      </div>

      <div class="app-layout">
        <article class="feature-panel">
          <div class="feature-panel-copy">
            <span class="feature-tag">专题</span>
            <h4>配置中心首页专题区</h4>
            <p>
              用统一的视觉层级承载系统总览、参数、组件和 AI 接入。
              这块保留更大的横向留白，做成类似首页主推荐位。
            </p>
          </div>
          <div class="feature-rail">
            <div class="feature-chip">
              <Location :size="16" />
              本地可用
            </div>
            <div class="feature-chip">
              <DataLine :size="16" />
              信息密度高
            </div>
            <div class="feature-chip">
              <Opportunity :size="16" />
              按需扩展
            </div>
          </div>
        </article>

        <div class="app-grid">
          <RouterLink v-for="app in appHighlights" :key="app.title" :to="app.to" class="app-card">
            <span class="app-tag">{{ app.tag }}</span>
            <strong>{{ app.title }}</strong>
            <p>{{ app.desc }}</p>
          </RouterLink>
        </div>
      </div>
    </section>

    <section class="dual-block">
      <article class="section-block">
        <div class="section-head">
          <div>
            <p class="eyebrow">新手教程</p>
            <h3>快速上手</h3>
          </div>
        </div>

        <div class="tutorial-list">
          <article v-for="tutorial in tutorials" :key="tutorial.title" class="tutorial-card">
            <span class="tutorial-type">{{ tutorial.category }}</span>
            <strong>{{ tutorial.title }}</strong>
            <p>{{ tutorial.date }}</p>
          </article>
        </div>
      </article>

      <article class="section-block">
        <div class="section-head">
          <div>
            <p class="eyebrow">客户案例</p>
            <h3>实践参考</h3>
          </div>
        </div>

        <div class="case-list">
          <article v-for="item in cases" :key="item.title" class="case-card">
            <span class="case-mark">案例</span>
            <strong>{{ item.title }}</strong>
            <p>{{ item.desc }}</p>
          </article>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.console-home {
  display: grid;
  gap: 18px;
  padding: 2px 2px 2px 0;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 0.8fr);
  gap: 16px;
}

.hero-banner,
.promo-card,
.section-block,
.notice-card,
.quick-card,
.plan-card,
.model-card,
.app-card,
.tutorial-card,
.case-card {
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.05);
  backdrop-filter: blur(10px);
}

.hero-banner {
  padding: 24px;
  background:
    radial-gradient(circle at 0% 0%, rgba(37, 99, 235, 0.12), transparent 34%),
    radial-gradient(circle at 100% 0%, rgba(14, 165, 233, 0.08), transparent 22%),
    rgba(255, 255, 255, 0.92);
}

.hero-banner h2,
.section-head h3,
.feature-panel h4 {
  margin: 0;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.hero-banner h2 {
  font-size: 34px;
  line-height: 1.08;
  max-width: 12ch;
}

.eyebrow {
  margin: 0 0 12px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.22em;
  text-transform: uppercase;
}

.hero-desc {
  margin: 14px 0 0;
  color: #475569;
  line-height: 1.75;
  max-width: 58ch;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 20px;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border-radius: 14px;
  padding: 11px 16px;
  text-decoration: none;
  font-weight: 600;
  transition: transform 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
}

.btn.primary {
  color: #eff6ff;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.18);
}

.btn.secondary {
  color: #0f172a;
  background: rgba(241, 245, 249, 0.96);
  border: 1px solid rgba(226, 232, 240, 0.95);
}

.btn:hover {
  transform: translateY(-1px);
}

.hero-stats {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 22px;
}

.stat-pill {
  min-width: 102px;
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.96), rgba(248, 250, 252, 0.96));
  border: 1px solid rgba(226, 232, 240, 0.95);
  display: grid;
  gap: 4px;
}

.stat-pill strong {
  font-size: 22px;
  color: #0f172a;
}

.stat-pill span {
  color: #64748b;
  font-size: 13px;
}

.hero-stack {
  display: grid;
  gap: 16px;
}

.promo-card {
  padding: 18px;
  min-height: 164px;
  display: grid;
  align-content: space-between;
}

.promo-card-accent {
  background:
    radial-gradient(circle at 0% 0%, rgba(37, 99, 235, 0.14), transparent 36%),
    rgba(255, 255, 255, 0.92);
}

.promo-card-soft {
  background:
    radial-gradient(circle at 100% 0%, rgba(14, 165, 233, 0.12), transparent 34%),
    rgba(255, 255, 255, 0.92);
}

.promo-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #2563eb;
}

.promo-badge,
.plan-tag,
.feature-tag,
.app-tag,
.tutorial-type,
.case-mark {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.promo-badge {
  color: #2563eb;
  background: rgba(239, 246, 255, 0.96);
}

.promo-badge.muted {
  color: #0f766e;
  background: rgba(236, 253, 245, 0.96);
}

.promo-card h3 {
  margin: 14px 0 0;
  font-size: 18px;
  color: #0f172a;
}

.promo-card p {
  margin: 8px 0 0;
  color: #64748b;
  line-height: 1.7;
}

.section-block {
  padding: 18px;
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.section-head h3 {
  font-size: 24px;
  line-height: 1.1;
}

.section-link,
.section-meta {
  color: #64748b;
  font-size: 13px;
}

.section-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  text-decoration: none;
  white-space: nowrap;
}

.notice-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.notice-card {
  padding: 16px;
  display: grid;
  gap: 10px;
}

.notice-date {
  font-size: 12px;
  color: #2563eb;
  letter-spacing: 0.04em;
}

.notice-card strong {
  color: #0f172a;
  font-size: 15px;
  line-height: 1.4;
}

.notice-card p {
  margin: 0;
  color: #64748b;
  line-height: 1.65;
  font-size: 13px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.quick-card {
  display: grid;
  grid-template-columns: 44px 1fr;
  gap: 12px;
  align-items: center;
  padding: 16px;
  text-decoration: none;
  color: inherit;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.quick-card:hover,
.plan-card:hover,
.model-card:hover,
.app-card:hover,
.tutorial-card:hover,
.case-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.08);
}

.quick-icon,
.model-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.14), rgba(59, 130, 246, 0.08));
  color: #1d4ed8;
}

.quick-card strong,
.plan-head strong,
.model-card strong,
.app-card strong,
.tutorial-card strong,
.case-card strong {
  color: #0f172a;
}

.quick-card p,
.plan-card p,
.model-card p,
.app-card p,
.tutorial-card p,
.case-card p {
  margin: 4px 0 0;
  color: #64748b;
  line-height: 1.65;
  font-size: 13px;
}

.plan-grid,
.model-grid {
  display: grid;
  gap: 12px;
}

.plan-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.plan-card,
.model-card {
  padding: 16px;
  display: grid;
  gap: 12px;
}

.plan-head {
  display: grid;
  gap: 10px;
}

.plan-tag {
  width: fit-content;
  color: #1d4ed8;
  background: rgba(239, 246, 255, 0.96);
}

.plan-footer {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.plan-label {
  display: block;
  color: #64748b;
  font-size: 12px;
  margin-bottom: 4px;
}

.plan-price {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.plan-action {
  border: 0;
  border-radius: 14px;
  padding: 10px 14px;
  font: inherit;
  cursor: pointer;
  color: #eff6ff;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
}

.model-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.model-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.model-category {
  color: #64748b;
  font-size: 12px;
}

.model-price {
  color: #2563eb;
  font-weight: 700;
}

.app-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 12px;
}

.feature-panel {
  padding: 20px;
  border-radius: 22px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  background:
    radial-gradient(circle at 0% 0%, rgba(37, 99, 235, 0.12), transparent 34%),
    rgba(255, 255, 255, 0.92);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.05);
  display: grid;
  gap: 18px;
}

.feature-panel h4 {
  font-size: 20px;
}

.feature-panel p {
  margin: 10px 0 0;
  color: #64748b;
  line-height: 1.75;
}

.feature-rail {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.feature-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 999px;
  background: rgba(241, 245, 249, 0.95);
  border: 1px solid rgba(226, 232, 240, 0.95);
  color: #334155;
  font-size: 13px;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.app-card {
  padding: 18px;
  display: grid;
  gap: 10px;
  text-decoration: none;
}

.app-tag {
  width: fit-content;
  color: #0f766e;
  background: rgba(236, 253, 245, 0.96);
}

.dual-block {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.tutorial-list,
.case-list {
  display: grid;
  gap: 12px;
}

.tutorial-card,
.case-card {
  padding: 16px;
}

.tutorial-type,
.case-mark {
  width: fit-content;
  color: #2563eb;
  background: rgba(239, 246, 255, 0.96);
}

.case-card {
  display: grid;
  gap: 10px;
}

@media (max-width: 1400px) {
  .model-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .plan-grid,
  .notice-list,
  .quick-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1180px) {
  .hero-grid,
  .app-layout,
  .dual-block {
    grid-template-columns: 1fr;
  }

  .notice-list,
  .quick-grid,
  .plan-grid,
  .model-grid,
  .app-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .hero-banner {
    padding: 20px;
  }

  .hero-banner h2 {
    font-size: 28px;
  }

  .section-block {
    padding: 16px;
  }

  .section-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
