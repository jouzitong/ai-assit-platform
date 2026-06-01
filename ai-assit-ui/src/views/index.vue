<template>
  <main class="page">
    <section class="hero">
      <p class="tag">EMP Dashboard</p>
      <h1>运营总览</h1>
      <p class="desc">首页聚合数据库核心指标、待办提醒与风险动态，聊天助手通过右下角机器人入口使用。</p>
    </section>

    <section class="stats-grid">
      <article class="metric-card">
        <p>员工总数</p>
        <h3>1,286</h3>
        <span>较上周 +1.8%</span>
      </article>
      <article class="metric-card">
        <p>今日出勤率</p>
        <h3>97.2%</h3>
        <span>异常打卡 9 条</span>
      </article>
      <article class="metric-card">
        <p>本月人力成本</p>
        <h3>¥8.42M</h3>
        <span>同比 +4.1%</span>
      </article>
      <article class="metric-card">
        <p>待审批流程</p>
        <h3>23</h3>
        <span>高优先级 6 条</span>
      </article>
    </section>

    <section class="grid two-col">
      <article class="panel-card">
        <h2>待办事项</h2>
        <ul class="todo-list">
          <li>审批 5 月绩效调薪名单（截止今天 18:00）</li>
          <li>确认研发中心本周加班统计报表</li>
          <li>复核上海分部编制预算偏差说明</li>
          <li>发布下月考勤规则调整通知</li>
        </ul>
      </article>

      <article class="panel-card">
        <h2>数据监控提醒</h2>
        <ul class="todo-list">
          <li>客服团队离职率连续 2 周上升</li>
          <li>销售一部异常打卡率高于阈值 1.5%</li>
          <li>绩效 S 级占比低于季度目标</li>
          <li>人力成本与预算偏差超过预警线</li>
        </ul>
      </article>
    </section>
  </main>
</template>
