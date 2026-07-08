export const pageTitle = 'ECharts 通用组件'
export const pageDescription = '参考 list 组件的复用方式，当前沉淀三类常用图表：折线图、组合图、雷达图。页面里的数据是静态示例，业务页可直接替换 props。'

export const chartExamples = [
  {
    key: 'line',
    title: '折线图',
    desc: '适合趋势追踪、环比变化和多序列对比。',
    component: 'line',
    props: {
      categories: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
      unit: 'h',
      area: true,
      series: [
        { name: '研发人均投入', data: [6.2, 6.8, 7.1, 6.9, 7.4, 5.8, 4.2] },
        { name: '客服人均投入', data: [5.5, 5.9, 6.2, 6.1, 6.5, 6.3, 5.7] }
      ]
    }
  },
  {
    key: 'combo',
    title: '组合图',
    desc: '柱状 + 折线，适合规模和效率指标放在同一视图里。',
    component: 'combo',
    props: {
      categories: ['1月', '2月', '3月', '4月', '5月', '6月'],
      leftUnit: '人',
      rightUnit: '%',
      barSeries: [
        { name: '招聘入职', data: [32, 28, 35, 31, 42, 38] },
        { name: '离职人数', data: [11, 8, 9, 12, 10, 13] }
      ],
      lineSeries: [
        { name: '留存率', data: [92, 94, 93, 91, 95, 94], yAxisIndex: 1 }
      ]
    }
  },
  {
    key: 'radar',
    title: '雷达图',
    desc: '适合能力评估、组织成熟度和多维诊断。',
    component: 'radar',
    props: {
      indicators: [
        { name: '数据接入', max: 100 },
        { name: '模型治理', max: 100 },
        { name: '流程编排', max: 100 },
        { name: '权限审计', max: 100 },
        { name: '效果评估', max: 100 },
        { name: '知识沉淀', max: 100 }
      ],
      series: [
        { name: '当前版本', data: [82, 76, 88, 72, 80, 74], opacity: 0.22 },
        { name: '目标版本', data: [95, 90, 94, 90, 92, 88], opacity: 0.1 }
      ]
    }
  }
]

export const usageTips = [
  '业务页只传 categories / series / indicators 等纯数据结构，避免把接口请求耦合到组件内部。',
  '如果需要定制 tooltip、grid、markLine 或 visualMap，使用 option 覆盖即可。',
  '三类封装底下统一复用 BaseChart，后续补饼图、漏斗图时可以直接沿用。'
]
