export const models = [
  { value: 'gpt-4.1', label: 'GPT-4.1' },
  { value: 'gpt-4o', label: 'GPT-4o' },
  { value: 'deepseek-r1', label: 'DeepSeek-R1' }
]

export const initialExecutions = [
  {
    title: '系统启动',
    detail: '等待用户输入问题，准备检索元数据、指标定义和可用数据集。',
    tone: 'neutral',
    active: false
  },
  {
    title: '上下文装载',
    detail: '已加载员工主题域、考勤指标、绩效口径和成本分析规则。',
    tone: 'info',
    active: true
  }
]

export const initialStages = [
  { name: '需求理解', desc: '已识别最近 30 天各部门人力成本变化与异常波动原因', status: 'done' },
  { name: '数据源选择', desc: '已锁定员工、考勤、成本台账与部门维度数据源', status: 'running' },
  { name: 'SQL / DSL 生成', desc: '正在生成按部门汇总并过滤异常值的查询语句', status: 'pending' },
  { name: '结果校验', desc: '准备检查口径一致性、缺失值和极端波动', status: 'pending' },
  { name: '结论输出', desc: '将输出摘要结论、重点异常和追问建议', status: 'pending' }
]

export const initialHistoryList = [
  {
    id: 'history-1',
    title: '最近30天人力成本波动分析',
    summary: '聚焦研发、销售部门的成本上涨原因与异常波动。',
    tag: '人力成本',
    time: '今天 14:32',
    active: true,
    pinned: true
  },
  {
    id: 'history-2',
    title: '客服团队夜班补贴异常排查',
    summary: '检查夜班补贴规则、发放范围与异常人员。',
    tag: '考勤补贴',
    time: '今天 11:08',
    active: false,
    pinned: false
  },
  {
    id: 'history-3',
    title: '研发中心编制与预算偏差对比',
    summary: '对比编制扩张、预算占用和实际执行差异。',
    tag: '研发组织',
    time: '昨天 18:45',
    active: false,
    pinned: false
  },
  {
    id: 'history-4',
    title: '销售部门提成成本趋势复盘',
    summary: '梳理提成支出变化及其对月度成本的影响。',
    tag: '销售提成',
    time: '昨天 16:20',
    active: false,
    pinned: false
  },
  {
    id: 'history-5',
    title: '月度绩效分布与离职率联动',
    summary: '观察绩效结构变化与离职率的联动关系。',
    tag: '绩效分析',
    time: '06-01 09:14',
    active: false,
    pinned: false
  }
]

export const resultRows = [
  { dept: '研发中心', cost: '¥2.48M', change: '+12.4%', risk: '扩招带动上涨' },
  { dept: '销售一部', cost: '¥1.96M', change: '+8.1%', risk: '提成支出增加' },
  { dept: '客服团队', cost: '¥1.18M', change: '+15.7%', risk: '夜班补贴异常' },
  { dept: '职能支持', cost: '¥0.92M', change: '+3.2%', risk: '基本稳定' }
]

export const pieSegments = [
  { label: '研发', value: 38, color: '#2563eb' },
  { label: '销售', value: 30, color: '#0ea5e9' },
  { label: '客服', value: 18, color: '#10b981' },
  { label: '职能', value: 14, color: '#f59e0b' }
]

export const barSeries = [
  { label: '研发', value: 82 },
  { label: '销售', value: 64 },
  { label: '客服', value: 71 },
  { label: '职能', value: 48 }
]

export const placeholder = '例如：帮我分析最近 30 天各部门人力成本变化，并找出异常波动原因'
