export const heroSummary = {
  title: '今天想让 AI 帮你处理什么？',
  placeholder: '例如：帮我分析本周知识库同步失败原因，并列出需要处理的风险项',
  composerTools: ['附件', '工具', '知识库'],
  modelLabel: 'GPT-4.1 Mini',
  voiceLabel: '语音输入'
}

export const promptSuggestions = [
  '帮我总结今天平台里的高优先级风险项',
  '查询最近 7 天知识库同步失败的任务',
  '生成一份本周组织运营简报',
  '对比各业务线的人力成本异常波动'
]

export const quickEntries = [
  { title: '知识库管理', description: '查看数据源、同步任务和可见范围设置。', to: '/knowledge', meta: '内容资产' },
  { title: '模型与 Provider', description: '进入系统管理，维护模型、凭证和本地知识库。', to: '/settings/system/ai', meta: '系统配置' },
  { title: '智能问数会话', description: '查看当前分析会话、执行链路和输出结果。', to: '/query', meta: '数据分析' },
  { title: '考勤看板', description: '进入组织出勤趋势、异常打卡和部门统计。', to: '/emp/attendance', meta: '组织运营' },
  { title: '绩效洞察', description: '查看绩效波动、团队对比和重点人员画像。', to: '/emp/performance', meta: '经营分析' },
  { title: '人力成本分析', description: '跟踪预算偏差、月度成本构成和异常波动。', to: '/emp/cost', meta: '成本控制' }
]

export const focusPanels = [
  {
    title: '待推进事项',
    eyebrow: 'Priority Queue',
    items: [
      '审批 5 月绩效调薪名单，今天 18:00 前需要完成最终确认',
      '复核知识库同步失败的 2 条任务，避免晚间索引延迟',
      '确认研发中心本周加班统计报表并同步给人力 BP'
    ]
  },
  {
    title: '平台监控提醒',
    eyebrow: 'Signals',
    items: [
      '客服团队离职率连续 2 周上升，需要补充组织画像分析',
      '销售一部异常打卡率高于阈值 1.5%，建议切到考勤看板复核',
      '本地知识库主表新增 3 条记录未绑定 owner'
    ]
  }
]

export const activityFeed = [
  { time: '10:24', title: '知识库同步完成', detail: '市场分析库完成增量同步，新增 126 条文档。', type: 'success' },
  { time: '09:58', title: '模型灰度已开启', detail: 'gpt-4.1-mini 灰度流量提升到 20%。', type: 'info' },
  { time: '09:16', title: '工作流执行失败', detail: 'query-planning 节点出现 2 次超时，等待复核。', type: 'warning' }
]

export const spotlightCards = [
  { label: '本周新增知识源', value: '09', note: '多为业务看板配置' },
  { label: '平均响应耗时', value: '1.8s', note: '核心接口稳定' },
  { label: '异常告警关闭率', value: '87%', note: '较昨日提升 6%' }
]

export const calendarItems = [
  { day: 'Mon', date: '06/22', title: '绩效调薪审批', time: '10:00 - 11:00' },
  { day: 'Tue', date: '06/23', title: '知识库同步巡检', time: '14:00 - 14:30' },
  { day: 'Wed', date: '06/24', title: '问数链路复盘', time: '16:00 - 17:00' },
  { day: 'Thu', date: '06/25', title: '预算偏差确认', time: '11:00 - 12:00' }
]
