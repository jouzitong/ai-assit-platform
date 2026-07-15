export type ApplicationComponentControl = 'text' | 'number' | 'boolean' | 'json'

export interface ApplicationComponentParameter {
  key: string
  label: string
  type: string
  control: ApplicationComponentControl
  required?: boolean
  defaultValue: unknown
  description: string
}

export interface ApplicationComponentEvent {
  name: string
  description: string
}

export interface ApplicationComponentDefinition {
  key: string
  name: string
  category: string
  version: string
  sourcePath: string
  description: string
  useCases: string[]
  tags: string[]
  parameters: ApplicationComponentParameter[]
  events: ApplicationComponentEvent[]
}

const listSchemaExample = {
  id: 'business-list',
  version: '1.0.0',
  title: '业务数据列表',
  component: 'zg-common-list',
  fields: [
    { key: 'name', name: 'name', label: '名称', field: ['name'] },
    { key: 'status', name: 'status', label: '状态', field: ['status'] },
  ],
  actions: [
    { key: 'create', name: '新建', action: 'CREATE', type: 'primary' },
  ],
  list_config: {
    variant: 'workbench',
    itemType: 'table',
    pagination: { enabled: true, pageSize: 10, pageSizeOptions: [10, 20, 50] },
  },
}

const formSchemaExample = {
  id: 'business-form',
  version: '1.0.0',
  title: '基础信息',
  component: 'zg-common-form',
  fields: [
    {
      key: 'name',
      name: 'name',
      label: '名称',
      component: 'zg-input',
      type: 'text',
      options: { required: true, placeholder: '请输入名称' },
    },
  ],
  actions: [
    { key: 'save', name: '保存', action: 'SAVE', type: 'primary' },
  ],
  form_config: {
    variant: 'workbench',
    columns: 2,
    actionsAlign: 'right',
  },
  data: { name: '' },
}

export const APPLICATION_COMPONENT_MANIFEST: ApplicationComponentDefinition[] = [
  {
    key: 'zg-list-main-layout',
    name: '通用列表渲染器',
    category: '数据展示',
    version: '1.0.0',
    sourcePath: 'src/application/renderers/list/ListMainLayout.vue',
    description: '基于 Schema 组装标题、页签、树、筛选、摘要、数据列表与分页的通用业务列表容器。',
    useCases: ['后台管理列表', '带树分类的数据浏览', '由 Render JSON 动态驱动的列表页'],
    tags: ['list', 'schema', 'renderer'],
    parameters: [
      { key: 'schema', label: '列表 Schema', type: 'ListRendererSchema', control: 'json', required: true, defaultValue: listSchemaExample, description: '定义列表结构、字段、筛选、动作和分页行为。' },
      { key: 'records', label: '列表数据', type: 'Record<string, unknown>[]', control: 'json', defaultValue: [], description: '当前页用于渲染的数据记录。' },
      { key: 'treeData', label: '树节点数据', type: 'RendererTreeNode[]', control: 'json', defaultValue: [], description: '启用树列表布局时的左侧树数据。' },
      { key: 'loading', label: '加载状态', type: 'boolean', control: 'boolean', defaultValue: false, description: '控制列表的加载反馈。' },
      { key: 'total', label: '数据总数', type: 'number', control: 'number', defaultValue: 0, description: '用于分页器计算总页数。' },
    ],
    events: [
      { name: 'action', description: '顶部动作触发时输出。' },
      { name: 'itemAction', description: '行内动作触发时输出动作和当前记录。' },
      { name: 'queryChange', description: '筛选、页签或分页状态变化时输出。' },
      { name: 'reload', description: '用户主动搜索、重置或翻页时请求外部刷新数据。' },
    ],
  },
  {
    key: 'form-main-layout',
    name: '通用表单渲染器',
    category: '表单交互',
    version: '1.0.0',
    sourcePath: 'src/application/renderers/form/FormMainLayout.vue',
    description: '根据 Schema 动态生成分组表单、字段控件和操作区的通用表单容器。',
    useCases: ['业务对象新增与编辑', '基础信息查看', '由 Render JSON 生成的动态表单'],
    tags: ['form', 'schema', 'renderer'],
    parameters: [
      { key: 'schema', label: '表单 Schema', type: 'FormRendererSchema', control: 'json', required: true, defaultValue: formSchemaExample, description: '定义字段、分组、动作、关系与表单布局。' },
      { key: 'modelValue', label: '表单数据', type: 'Record<string, unknown>', control: 'json', defaultValue: {}, description: '表单受控数据对象。' },
      { key: 'readonly', label: '只读模式', type: 'boolean', control: 'boolean', defaultValue: false, description: '开启后所有字段仅展示不可编辑。' },
    ],
    events: [
      { name: 'action', description: '表单操作按钮触发时输出。' },
      { name: 'change', description: '字段变化时输出字段、当前值与全量数据。' },
      { name: 'update:modelValue', description: '表单受控值更新时输出。' },
    ],
  },
  {
    key: 'line-chart-renderer',
    name: '折线图渲染器',
    category: '数据可视化',
    version: '1.0.0',
    sourcePath: 'src/application/renderers/echarts/LineChartRenderer.vue',
    description: '面向趋势、多序列对比和面积趋势的 ECharts 折线图渲染器。',
    useCases: ['时间趋势分析', '多指标变化对比', '累计值与面积趋势'],
    tags: ['chart', 'line', 'echarts'],
    parameters: [
      { key: 'categories', label: 'X 轴分类', type: 'Array<string | number>', control: 'json', required: true, defaultValue: ['1月', '2月', '3月'], description: '横轴分类或时间维度数据。' },
      { key: 'series', label: '折线序列', type: 'LineChartSeries[]', control: 'json', required: true, defaultValue: [{ name: '成交量', data: [128, 176, 204] }], description: '折线序列及其数值、颜色和样式。' },
      { key: 'option', label: 'ECharts 扩展配置', type: 'EChartsOption', control: 'json', defaultValue: {}, description: '与默认配置合并的 ECharts option。' },
      { key: 'height', label: '图表高度', type: 'number | string', control: 'number', defaultValue: 320, description: '图表容器高度，数字以 px 计。' },
      { key: 'unit', label: '数值单位', type: 'string', control: 'text', defaultValue: '', description: '工具提示与 Y 轴展示的数值单位。' },
      { key: 'colors', label: '颜色列表', type: 'string[]', control: 'json', defaultValue: ['var(--app-chart-color-1)', 'var(--app-chart-color-2)', 'var(--app-chart-color-3)'], description: '按序列顺序应用的颜色数组。' },
      { key: 'smooth', label: '平滑曲线', type: 'boolean', control: 'boolean', defaultValue: true, description: '是否使用平滑曲线。' },
      { key: 'area', label: '面积填充', type: 'boolean', control: 'boolean', defaultValue: false, description: '是否显示折线下方的面积填充。' },
      { key: 'showSymbol', label: '显示数据点', type: 'boolean', control: 'boolean', defaultValue: false, description: '是否常驻显示序列数据点。' },
      { key: 'legend', label: '显示图例', type: 'boolean', control: 'boolean', defaultValue: true, description: '是否显示图例。' },
      { key: 'loading', label: '加载状态', type: 'boolean', control: 'boolean', defaultValue: false, description: '是否显示 ECharts 加载效果。' },
    ],
    events: [],
  },
  {
    key: 'combo-chart-renderer',
    name: '柱线组合图渲染器',
    category: '数据可视化',
    version: '1.0.0',
    sourcePath: 'src/application/renderers/echarts/ComboChartRenderer.vue',
    description: '将柱状数据与折线指标放在双 Y 轴中对比的组合图渲染器。',
    useCases: ['规模与比率联合分析', '实际值与趋势线对比', '双单位指标看板'],
    tags: ['chart', 'combo', 'echarts'],
    parameters: [
      { key: 'categories', label: 'X 轴分类', type: 'Array<string | number>', control: 'json', required: true, defaultValue: ['1月', '2月', '3月'], description: '横轴分类或时间维度数据。' },
      { key: 'barSeries', label: '柱状序列', type: 'ComboBarSeries[]', control: 'json', required: true, defaultValue: [{ name: '订单量', data: [120, 168, 196] }], description: '主 Y 轴的柱状数据序列。' },
      { key: 'lineSeries', label: '折线序列', type: 'ComboLineSeries[]', control: 'json', defaultValue: [{ name: '转化率', data: [18, 24, 27], yAxisIndex: 1 }], description: '可选的次 Y 轴折线数据序列。' },
      { key: 'option', label: 'ECharts 扩展配置', type: 'EChartsOption', control: 'json', defaultValue: {}, description: '与默认配置合并的 ECharts option。' },
      { key: 'height', label: '图表高度', type: 'number | string', control: 'number', defaultValue: 340, description: '图表容器高度，数字以 px 计。' },
      { key: 'colors', label: '颜色列表', type: 'string[]', control: 'json', defaultValue: ['var(--app-chart-color-1)', 'var(--app-chart-color-2)', 'var(--app-chart-color-3)'], description: '按序列顺序应用的颜色数组。' },
      { key: 'legend', label: '显示图例', type: 'boolean', control: 'boolean', defaultValue: true, description: '是否显示图例。' },
      { key: 'loading', label: '加载状态', type: 'boolean', control: 'boolean', defaultValue: false, description: '是否显示 ECharts 加载效果。' },
      { key: 'leftUnit', label: '左轴单位', type: 'string', control: 'text', defaultValue: '', description: '左侧 Y 轴的数值单位。' },
      { key: 'rightUnit', label: '右轴单位', type: 'string', control: 'text', defaultValue: '%', description: '右侧 Y 轴的数值单位。' },
    ],
    events: [],
  },
  {
    key: 'radar-chart-renderer',
    name: '雷达图渲染器',
    category: '数据可视化',
    version: '1.0.0',
    sourcePath: 'src/application/renderers/echarts/RadarChartRenderer.vue',
    description: '用于多维指标对比、能力轮廓与综合评估的 ECharts 雷达图渲染器。',
    useCases: ['多维能力画像', '指标达成度对比', '多对象综合评估'],
    tags: ['chart', 'radar', 'echarts'],
    parameters: [
      { key: 'indicators', label: '雷达指标', type: 'RadarIndicator[]', control: 'json', required: true, defaultValue: [{ name: '稳定性', max: 100 }, { name: '易用性', max: 100 }, { name: '性能', max: 100 }], description: '雷达轴名称与每个维度的最大值。' },
      { key: 'series', label: '雷达序列', type: 'RadarSeries[]', control: 'json', required: true, defaultValue: [{ name: '当前方案', data: [86, 78, 92] }], description: '各对比对象在雷达维度上的数值。' },
      { key: 'option', label: 'ECharts 扩展配置', type: 'EChartsOption', control: 'json', defaultValue: {}, description: '与默认配置合并的 ECharts option。' },
      { key: 'height', label: '图表高度', type: 'number | string', control: 'number', defaultValue: 360, description: '图表容器高度，数字以 px 计。' },
      { key: 'colors', label: '颜色列表', type: 'string[]', control: 'json', defaultValue: ['var(--app-chart-color-1)', 'var(--app-chart-color-2)', 'var(--app-chart-color-3)'], description: '按序列顺序应用的颜色数组。' },
      { key: 'legend', label: '显示图例', type: 'boolean', control: 'boolean', defaultValue: true, description: '是否显示图例。' },
      { key: 'loading', label: '加载状态', type: 'boolean', control: 'boolean', defaultValue: false, description: '是否显示 ECharts 加载效果。' },
    ],
    events: [],
  },
]

export function findApplicationComponent(componentKey?: string) {
  return APPLICATION_COMPONENT_MANIFEST.find(item => item.key === componentKey)
}
