import {
  COMBO_CHART_RENDERER_CATALOG_ENTRY,
  BAR_CHART_RENDERER_CATALOG_ENTRY,
  FORM_RENDERER_CATALOG_ENTRY,
  FUNNEL_CHART_RENDERER_CATALOG_ENTRY,
  GAUGE_CHART_RENDERER_CATALOG_ENTRY,
  HEATMAP_CHART_RENDERER_CATALOG_ENTRY,
  LINE_CHART_RENDERER_CATALOG_ENTRY,
  LIST_RENDERER_CATALOG_ENTRY,
  PIE_CHART_RENDERER_CATALOG_ENTRY,
  RADAR_CHART_RENDERER_CATALOG_ENTRY,
  SCATTER_CHART_RENDERER_CATALOG_ENTRY,
  type ApplicationRendererExposure,
} from './registry/catalog'
import type { FormRendererSchema } from './renderers/form/types'
import type { ListRendererSchema } from './schema/list'

export type ApplicationComponentControl = 'text' | 'number' | 'boolean' | 'json'
export type ApplicationComponentParameterItemType = 'boolean' | 'number' | 'object' | 'string'

export interface ApplicationComponentParameter {
  key: string
  label: string
  type: string
  control: ApplicationComponentControl
  /** 自定义空数组无法从默认案例推断元素类型时，显式声明数组元素的 JSON 类型。 */
  itemType?: ApplicationComponentParameterItemType
  required?: boolean
  defaultValue: unknown
  description: string
}

export interface ApplicationComponentEvent {
  name: string
  description: string
}

export interface ApplicationComponentDocumentation {
  summary: string
  usageGuide: string
  limitations: string
  notes: string
}

export interface ApplicationRenderNode {
  id: string
  component: string
  componentVersion?: string
  props?: Record<string, unknown>
  layout?: Record<string, unknown>
  datasource?: Record<string, unknown>
  bindings?: Record<string, unknown>
  events?: Array<Record<string, unknown>>
  actions?: Array<Record<string, unknown>>
  children?: ApplicationRenderNode[]
}

export interface ApplicationRenderDocument {
  protocol: 'render-json'
  protocolVersion: string
  pageId: string
  revision?: string
  root: ApplicationRenderNode
}

export interface ApplicationComponentExample {
  key: string
  name: string
  description: string
  renderDocument: ApplicationRenderDocument
}

export interface ApplicationComponentDefinition {
  key: string
  aliases: readonly string[]
  name: string
  category: string
  version: string
  sourcePath: string
  exposure: ApplicationRendererExposure
  description: string
  useCases: string[]
  tags: string[]
  documentation: ApplicationComponentDocumentation
  parameters: ApplicationComponentParameter[]
  events: ApplicationComponentEvent[]
  examples: ApplicationComponentExample[]
}

const COMPONENT_VERSION = '1.0.0'
const RENDER_PROTOCOL_VERSION = '1.0.0'
const CHART_COLORS = [
  'var(--app-chart-color-1)',
  'var(--app-chart-color-2)',
  'var(--app-chart-color-3)',
  'var(--app-chart-color-4)',
  'var(--app-chart-color-5)',
  'var(--app-chart-color-6)',
  'var(--app-chart-color-7)',
  'var(--app-chart-color-8)',
]

function createRenderDocument(options: {
  pageId: string
  component: string
  props: Record<string, unknown>
  layout?: Record<string, unknown>
}): ApplicationRenderDocument {
  return {
    protocol: 'render-json',
    protocolVersion: RENDER_PROTOCOL_VERSION,
    pageId: options.pageId,
    root: {
      id: `${options.pageId}-root`,
      component: options.component,
      componentVersion: COMPONENT_VERSION,
      props: options.props,
      ...(options.layout ? { layout: options.layout } : {}),
    },
  }
}

const listSchemaExample = {
  id: 'business-list',
  version: COMPONENT_VERSION,
  title: '任务中心',
  component: 'zg-common-tree-list',
  tree: {
    component: 'group-list',
    title: '任务分组',
  },
  tab: {
    activeTab: 'all',
    tabs: [
      { key: 'all', label: '全部任务' },
      { key: 'mine', label: '我负责的' },
    ],
  },
  filters: [
    {
      key: 'keyword',
      label: '任务名称',
      component: 'zg-input',
      options: {
        placeholder: '输入名称后回车搜索',
      },
    },
    {
      key: 'status',
      label: '状态',
      component: 'zg-selector',
      options: {
        list: [
          { key: '待处理', value: 'pending' },
          { key: '进行中', value: 'running' },
          { key: '已完成', value: 'completed' },
        ],
        clearable: true,
        submitOnChange: true,
      },
    },
  ],
  fields: [
    { key: 'id', name: '任务编号', field: ['id'], options: { styles: { width: 140 } } },
    { key: 'name', name: '任务名称', field: ['name'] },
    { key: 'owner', name: '负责人', field: ['owner'] },
    { key: 'status', name: '状态', field: ['status'] },
  ],
  actions: [
    { key: 'create', name: '新建', action: 'CREATE', options: { type: 'primary' } },
  ],
  summary: {
    cards: [
      { key: 'pending', label: '待处理', value: 2, hint: '需要尽快跟进' },
      { key: 'running', label: '进行中', value: 1, hint: '正在处理' },
    ],
  },
  list_config: {
    variant: 'workbench',
    itemType: 'table',
    actionColumns: [
      { key: 'view', name: '查看', action: 'VIEW', options: { type: 'primary' } },
      { key: 'delete', name: '删除', action: 'DELETE', options: { type: 'danger' } },
    ],
    pagination: { enabled: true, pageSize: 10, pageSizeOptions: [10, 20, 50] },
  },
} satisfies ListRendererSchema

const listRecordsExample = [
  { id: 'TASK-001', name: '核对组件资产文档', owner: '平台组', status: '进行中' },
  { id: 'TASK-002', name: '补充运行时示例', owner: '前端组', status: '待处理' },
  { id: 'TASK-003', name: '验证知识库同步', owner: 'AI 组', status: '已完成' },
]

const listTreeDataExample = [
  { key: 'all', label: '全部任务', count: 3 },
  { key: 'platform', label: '平台组', count: 1 },
  { key: 'frontend', label: '前端组', count: 1 },
  { key: 'ai', label: 'AI 组', count: 1 },
]

const listDataExample = {
  records: listRecordsExample,
  treeData: listTreeDataExample,
  total: listRecordsExample.length,
}

const listStateExample = {
  loading: false,
  empty: false,
}

const formSchemaExample = {
  id: 'business-form',
  version: COMPONENT_VERSION,
  title: '组件资产信息',
  component: 'zg-common-form',
  fields: [
    {
      key: 'name',
      name: 'name',
      label: '资产名称',
      component: 'zg-input',
      type: 'text',
      options: { required: true, placeholder: '请输入资产名称', labelPosition: 'left' },
    },
    {
      key: 'category',
      name: 'category',
      label: '分类',
      component: 'zg-selector',
      type: 'select',
      list: [
        { key: '数据展示', value: '数据展示' },
        { key: '表单交互', value: '表单交互' },
        { key: '数据可视化', value: '数据可视化' },
      ],
      options: { clearable: true, labelPosition: 'top' },
    },
    {
      key: 'description',
      name: 'description',
      label: '能力说明',
      component: 'zg-input',
      type: 'textarea',
      options: { rows: 4, placeholder: '说明组件能力和使用边界', labelPosition: 'inline' },
    },
    {
      key: 'enabled',
      name: 'enabled',
      label: '启用状态',
      type: 'switch',
      options: { labelPosition: 'right' },
    },
  ],
  groups: [
    {
      key: 'base',
      title: '基础信息',
      description: '维护组件资产的名称与分类。',
      fields: ['name', 'category'],
      columns: 2,
    },
    {
      key: 'capability',
      title: '能力配置',
      fields: ['description', 'enabled'],
      columns: 1,
    },
  ],
  actions: [
    { key: 'save', name: '保存', action: 'SAVE', options: { type: 'primary' } },
    { key: 'reset', name: '重置', action: 'RESET' },
  ],
  form_config: {
    variant: 'workbench',
    columns: 2,
    actionsAlign: 'right',
    description: '用于演示动态字段、分组和受控表单数据。',
  },
  data: {},
} satisfies FormRendererSchema

const formModelValueExample = {
  name: '通用列表渲染器',
  category: '数据展示',
  description: '通过 Schema 和受控数据渲染业务列表。',
  enabled: true,
}

const lineChartPropsExample = {
  categories: ['1月', '2月', '3月', '4月', '5月', '6月'],
  series: [
    { name: '成交额', data: [128, 176, 204, 198, 246, 286], smooth: true },
    { name: '目标值', data: [140, 160, 190, 210, 230, 260], smooth: true, showSymbol: true },
  ],
  option: {},
  height: 320,
  unit: ' 万元',
  colors: CHART_COLORS,
  smooth: true,
  area: true,
  showSymbol: false,
  legend: true,
  loading: false,
}

const comboChartPropsExample = {
  categories: ['1月', '2月', '3月', '4月', '5月', '6月'],
  barSeries: [
    { name: '访问量', data: [620, 760, 880, 940, 1080, 1260], barMaxWidth: 26 },
    { name: '下单量', data: [132, 168, 214, 238, 286, 324], barMaxWidth: 26 },
  ],
  lineSeries: [
    { name: '转化率', data: [21.3, 22.1, 24.3, 25.3, 26.5, 25.7], yAxisIndex: 1, smooth: true },
  ],
  option: {},
  height: 340,
  colors: CHART_COLORS,
  legend: true,
  loading: false,
  leftUnit: ' 次',
  rightUnit: '%',
}

const radarChartPropsExample = {
  indicators: [
    { name: '稳定性', max: 100 },
    { name: '易用性', max: 100 },
    { name: '性能', max: 100 },
    { name: '安全性', max: 100 },
    { name: '可维护性', max: 100 },
  ],
  series: [
    { name: '当前版本', data: [86, 78, 92, 88, 82], opacity: 0.18 },
    { name: '目标版本', data: [92, 90, 95, 94, 91], opacity: 0.08 },
  ],
  option: {},
  height: 360,
  colors: CHART_COLORS,
  legend: true,
  loading: false,
}

const pieChartPropsExample = {
  data: [
    { name: '直接访问', value: 580 },
    { name: '搜索引擎', value: 420 },
    { name: '外部链接', value: 260 },
    { name: '站内推荐', value: 180 },
  ],
  option: {},
  height: 320,
  colors: CHART_COLORS,
  donut: true,
  legend: true,
  loading: false,
}

const barChartPropsExample = {
  categories: ['一线城市', '新一线', '二线城市', '其他'],
  series: [
    { name: '本季度', data: [820, 740, 620, 410], barMaxWidth: 30 },
    { name: '上季度', data: [760, 680, 570, 380], barMaxWidth: 30 },
  ],
  option: {},
  height: 320,
  unit: ' 万',
  colors: CHART_COLORS,
  legend: true,
  horizontal: false,
  stacked: false,
  loading: false,
}

const gaugeChartPropsExample = {
  value: 78,
  min: 0,
  max: 100,
  unit: '%',
  option: {},
  height: 280,
  colors: CHART_COLORS,
  loading: false,
}

const funnelChartPropsExample = {
  data: [
    { name: '访问页面', value: 1000 },
    { name: '提交表单', value: 720 },
    { name: '完成支付', value: 480 },
    { name: '复购用户', value: 260 },
  ],
  option: {},
  height: 320,
  colors: CHART_COLORS,
  legend: true,
  loading: false,
}

const scatterChartPropsExample = {
  series: [
    { name: '客户样本', data: [[12, 18], [18, 26], [24, 32], [30, 44], [38, 51]] },
    { name: '重点客户', data: [[16, 40], [28, 58], [42, 72]] },
  ],
  option: {},
  height: 320,
  colors: CHART_COLORS,
  xName: '客单价',
  yName: '生命周期价值',
  legend: true,
  loading: false,
}

const heatmapChartPropsExample = {
  xCategories: ['周一', '周二', '周三', '周四', '周五'],
  yCategories: ['上午', '中午', '下午', '晚上'],
  data: [
    [0, 0, 42], [1, 0, 55], [2, 0, 48], [3, 0, 62], [4, 0, 70],
    [0, 1, 68], [1, 1, 72], [2, 1, 64], [3, 1, 80], [4, 1, 86],
    [0, 2, 58], [1, 2, 63], [2, 2, 60], [3, 2, 74], [4, 2, 78],
    [0, 3, 32], [1, 3, 38], [2, 3, 36], [3, 3, 45], [4, 3, 52],
  ],
  option: {},
  height: 320,
  colors: CHART_COLORS,
  loading: false,
}

export const APPLICATION_COMPONENT_MANIFEST: ApplicationComponentDefinition[] = [
  {
    ...LIST_RENDERER_CATALOG_ENTRY,
    description: '基于 Schema 组装标题、页签、树、筛选、摘要、数据列表与分页的通用业务列表容器。',
    useCases: ['后台管理列表', '带树分类的数据浏览', '由 Render JSON 动态驱动的列表页'],
    tags: ['list', 'schema', 'renderer'],
    documentation: {
      summary: '通用列表渲染器将页面标题、动作、页签、左侧树、筛选器、摘要卡片、数据表格和分页统一收敛到声明式 Schema 中。',
      usageGuide: '先在 schema 中声明字段、筛选项、动作和分页策略，再通过 records、treeData 与 total 传入受控数据。列表字段以 { key, name, field, options? } 为主，name 是字段说明而不是数据库 field key；历史 label 仅作兼容回退。布尔字段可在 options.mask 中声明 select 选项，表格和卡片都会显示 mask 后的文本。actions 与 actionColumns 的每一项使用 { key, name, action, options? }；筛选、页签和分页变化通过 queryChange/reload 事件交给 Runtime 或页面服务重新取数。',
      limitations: 'Renderer 不直接请求后端，也不执行 CREATE、VIEW、DELETE 等业务动作；外部必须监听语义事件并负责权限、请求、跳转和错误处理。mask 只做声明式值到文本映射，不替代业务数据校验。',
      notes: '示例同时覆盖树分组、页签、筛选、摘要、行内动作和分页，可直接作为列表类 Render JSON 的起始模板。',
    },
    parameters: [
      { key: 'schema', label: '列表 Schema', type: 'ListRendererSchema', control: 'json', required: true, defaultValue: listSchemaExample, description: '定义列表结构、字段、筛选、动作和分页行为；动作只使用 { key, name, action, options? }。' },
      { key: 'data', label: '列表数据入口', type: 'Partial<ListRendererData>', control: 'json', defaultValue: listDataExample, description: '推荐入口，统一提供 records、treeData 与 total。' },
      { key: 'state', label: '列表运行状态', type: 'ApplicationRendererState', control: 'json', defaultValue: listStateExample, description: '推荐入口，统一提供 loading、empty 与 error 等受控状态。' },
      { key: 'records', label: '列表数据（兼容）', type: 'Record<string, unknown>[]', control: 'json', defaultValue: listRecordsExample, description: '历史兼容入口；新配置优先使用 data.records。' },
      { key: 'treeData', label: '树节点数据（兼容）', type: 'RendererTreeNode[]', control: 'json', defaultValue: listTreeDataExample, description: '历史兼容入口；新配置优先使用 data.treeData。' },
      { key: 'loading', label: '加载状态（兼容）', type: 'boolean', control: 'boolean', defaultValue: false, description: '历史兼容入口；新配置优先使用 state.loading。' },
      { key: 'total', label: '数据总数（兼容）', type: 'number', control: 'number', defaultValue: listRecordsExample.length, description: '历史兼容入口；新配置优先使用 data.total。' },
    ],
    events: [
      { name: 'action', description: '顶部动作触发时输出。' },
      { name: 'itemAction', description: '行内动作触发时输出动作和当前记录。' },
      { name: 'queryChange', description: '筛选、页签或分页状态变化时输出。' },
      { name: 'reload', description: '用户主动搜索、重置或翻页时请求外部刷新数据。' },
    ],
    examples: [
      {
        key: 'task-center',
        name: '任务中心完整列表',
        description: '带树分组、页签、筛选、摘要卡片、行内动作与分页的完整列表案例。',
        renderDocument: createRenderDocument({
          pageId: 'component-example-list',
          component: 'zg-list-main-layout',
          props: {
            schema: listSchemaExample,
            data: listDataExample,
            state: listStateExample,
          },
          layout: { minHeight: '560px' },
        }),
      },
    ],
  },
  {
    ...FORM_RENDERER_CATALOG_ENTRY,
    description: '根据 Schema 动态生成分组表单、字段控件和操作区的通用表单容器。',
    useCases: ['业务对象新增与编辑', '基础信息查看', '由 Render JSON 生成的动态表单'],
    tags: ['form', 'schema', 'renderer'],
    documentation: {
      summary: '通用表单渲染器根据 Schema 生成字段、分组和动作区，并通过 modelValue 提供可控的编辑数据入口。',
      usageGuide: '在 schema.fields 中声明字段控件，在 schema.groups 中组织表单分区，通过 modelValue 传入初始值并监听 update:modelValue/change。字段设置 hide: true 后不渲染、不占栅格，但对应数据仍会保留；历史 field.options.hidden 继续兼容。字段区使用 12 栅格，普通字段默认占 6，textarea/code 默认占 12，可通过 field.options.span 配置 1-12；小容器降级为 6 栅格并自动压缩超长 span。字段 label 默认使用 left 与控件同行，可通过 field.options.labelPosition 配置 left、right、top、inline；form_config.labelWidth 控制同行 label 宽度。field 路径支持嵌套值读取和写回；formMode 支持 view、edit、add。schema.actions 的每一项使用 { key, name, action, options? }；保存动作通过 submit 事件交给 Runtime，并由 form_config.submit.executor 选择已注册提交器。',
      limitations: '当前字段控件以已注册的 Application 输入组件为准；远程选项、权限和服务端校验仍由 Runtime 或页面服务负责，未注册的提交器不会执行。',
      notes: '示例覆盖文本、选择、长文本、开关、双分组和顶部动作，适合作为新增或编辑类页面的基础模板。',
    },
    parameters: [
      { key: 'schema', label: '表单 Schema', type: 'FormRendererSchema', control: 'json', required: true, defaultValue: formSchemaExample, description: '定义字段、分组、动作、关系与表单布局；动作只使用 { key, name, action, options? }。' },
      { key: 'modelValue', label: '表单数据', type: 'Record<string, unknown>', control: 'json', defaultValue: formModelValueExample, description: '表单受控数据对象。' },
      { key: 'readonly', label: '只读模式', type: 'boolean', control: 'boolean', defaultValue: false, description: '开启后所有字段仅展示不可编辑。' },
      { key: 'formMode', label: '表单模式', type: 'FormRendererMode', control: 'text', defaultValue: 'edit', description: 'view、edit 或 add；view 自动只读，add 使用 form_config.defaultValues。' },
      { key: 'submitting', label: '提交状态', type: 'boolean', control: 'boolean', defaultValue: false, description: '提交期间禁用动作并为保存按钮显示加载状态。' },
    ],
    events: [
      { name: 'action', description: '表单操作按钮触发时输出。' },
      { name: 'change', description: '字段变化时输出字段、当前值与全量数据。' },
      { name: 'update:modelValue', description: '表单受控值更新时输出。' },
      { name: 'submit', description: '提交类动作触发时输出动作和完整表单数据。' },
      { name: 'reset', description: '重置后输出恢复的表单数据。' },
    ],
    examples: [
      {
        key: 'component-asset-form',
        name: '组件资产编辑表单',
        description: '包含基础信息、能力配置、受控数据和表单动作的完整案例。',
        renderDocument: createRenderDocument({
          pageId: 'component-example-form',
          component: 'form-main-layout',
          props: {
            schema: formSchemaExample,
            modelValue: formModelValueExample,
            readonly: false,
          },
          layout: { minHeight: '520px' },
        }),
      },
    ],
  },
  {
    ...LINE_CHART_RENDERER_CATALOG_ENTRY,
    description: '面向趋势、多序列对比和面积趋势的 ECharts 折线图渲染器。',
    useCases: ['时间趋势分析', '多指标变化对比', '累计值与面积趋势'],
    tags: ['chart', 'line', 'echarts'],
    documentation: {
      summary: '折线图渲染器面向连续趋势和多指标对比，内置坐标轴、图例、提示框、主题色和响应式尺寸处理。',
      usageGuide: 'categories 与每个 series.data 应保持相同顺序和长度；通过 unit、smooth、area、showSymbol 控制常用样式，复杂但可序列化的 ECharts 配置放入 option 覆盖。',
      limitations: 'option 必须是可序列化 JSON，不能包含 formatter 函数、DOM、ECharts 实例或其他运行时对象。数据加载和异常恢复由 Runtime 负责。',
      notes: '默认案例包含实际值和目标值两条序列，并开启面积填充，能够直接展示趋势对比效果。',
    },
    parameters: [
      { key: 'categories', label: 'X 轴分类', type: 'Array<string | number>', control: 'json', required: true, defaultValue: lineChartPropsExample.categories, description: '横轴分类或时间维度数据。' },
      { key: 'series', label: '折线序列', type: 'LineChartSeries[]', control: 'json', required: true, defaultValue: lineChartPropsExample.series, description: '折线序列及其数值、颜色和样式。' },
      { key: 'option', label: 'ECharts 扩展配置', type: 'EChartsOption', control: 'json', defaultValue: lineChartPropsExample.option, description: '与默认配置合并的可序列化 ECharts option。' },
      { key: 'height', label: '图表高度', type: 'number | string', control: 'number', defaultValue: lineChartPropsExample.height, description: '图表容器高度，数字以 px 计。' },
      { key: 'unit', label: '数值单位', type: 'string', control: 'text', defaultValue: lineChartPropsExample.unit, description: '工具提示与 Y 轴展示的数值单位。' },
      { key: 'colors', label: '颜色列表', type: 'string[]', control: 'json', defaultValue: lineChartPropsExample.colors, description: '按序列顺序应用的颜色数组。' },
      { key: 'smooth', label: '平滑曲线', type: 'boolean', control: 'boolean', defaultValue: lineChartPropsExample.smooth, description: '是否使用平滑曲线。' },
      { key: 'area', label: '面积填充', type: 'boolean', control: 'boolean', defaultValue: lineChartPropsExample.area, description: '是否显示折线下方的面积填充。' },
      { key: 'showSymbol', label: '显示数据点', type: 'boolean', control: 'boolean', defaultValue: lineChartPropsExample.showSymbol, description: '是否常驻显示序列数据点。' },
      { key: 'legend', label: '显示图例', type: 'boolean', control: 'boolean', defaultValue: lineChartPropsExample.legend, description: '是否显示图例。' },
      { key: 'loading', label: '加载状态', type: 'boolean', control: 'boolean', defaultValue: lineChartPropsExample.loading, description: '是否显示 ECharts 加载效果。' },
    ],
    events: [],
    examples: [
      {
        key: 'monthly-trend',
        name: '月度成交趋势',
        description: '实际值、目标值和面积趋势的双序列折线图案例。',
        renderDocument: createRenderDocument({
          pageId: 'component-example-line-chart',
          component: 'line-chart-renderer',
          props: lineChartPropsExample,
          layout: { minHeight: '360px' },
        }),
      },
    ],
  },
  {
    ...COMBO_CHART_RENDERER_CATALOG_ENTRY,
    description: '将柱状数据与折线指标放在双 Y 轴中对比的组合图渲染器。',
    useCases: ['规模与比率联合分析', '实际值与趋势线对比', '双单位指标看板'],
    tags: ['chart', 'combo', 'echarts'],
    documentation: {
      summary: '柱线组合图在同一分类轴中组织一组或多组柱状指标与折线指标，并使用左右双 Y 轴表达不同单位。',
      usageGuide: 'categories、barSeries 和 lineSeries 按分类顺序对齐；柱状数据默认使用左轴，折线数据默认使用右轴，可通过 yAxisIndex 显式覆盖。',
      limitations: '左右坐标轴应表达量纲清晰且可比较的指标；option 只能保存可序列化配置，不能包含 ECharts 回调函数或实例。',
      notes: '默认案例同时展示访问量、下单量与转化率，适合作为规模和效率联合分析的起始模板。',
    },
    parameters: [
      { key: 'categories', label: 'X 轴分类', type: 'Array<string | number>', control: 'json', required: true, defaultValue: comboChartPropsExample.categories, description: '横轴分类或时间维度数据。' },
      { key: 'barSeries', label: '柱状序列', type: 'ComboBarSeries[]', control: 'json', required: true, defaultValue: comboChartPropsExample.barSeries, description: '主 Y 轴的柱状数据序列。' },
      { key: 'lineSeries', label: '折线序列', type: 'ComboLineSeries[]', control: 'json', defaultValue: comboChartPropsExample.lineSeries, description: '可选的次 Y 轴折线数据序列。' },
      { key: 'option', label: 'ECharts 扩展配置', type: 'EChartsOption', control: 'json', defaultValue: comboChartPropsExample.option, description: '与默认配置合并的可序列化 ECharts option。' },
      { key: 'height', label: '图表高度', type: 'number | string', control: 'number', defaultValue: comboChartPropsExample.height, description: '图表容器高度，数字以 px 计。' },
      { key: 'colors', label: '颜色列表', type: 'string[]', control: 'json', defaultValue: comboChartPropsExample.colors, description: '按序列顺序应用的颜色数组。' },
      { key: 'legend', label: '显示图例', type: 'boolean', control: 'boolean', defaultValue: comboChartPropsExample.legend, description: '是否显示图例。' },
      { key: 'loading', label: '加载状态', type: 'boolean', control: 'boolean', defaultValue: comboChartPropsExample.loading, description: '是否显示 ECharts 加载效果。' },
      { key: 'leftUnit', label: '左轴单位', type: 'string', control: 'text', defaultValue: comboChartPropsExample.leftUnit, description: '左侧 Y 轴的数值单位。' },
      { key: 'rightUnit', label: '右轴单位', type: 'string', control: 'text', defaultValue: comboChartPropsExample.rightUnit, description: '右侧 Y 轴的数值单位。' },
    ],
    events: [],
    examples: [
      {
        key: 'traffic-conversion',
        name: '访问与转化分析',
        description: '双柱序列和转化率折线共用分类轴的双 Y 轴案例。',
        renderDocument: createRenderDocument({
          pageId: 'component-example-combo-chart',
          component: 'combo-chart-renderer',
          props: comboChartPropsExample,
          layout: { minHeight: '380px' },
        }),
      },
    ],
  },
  {
    ...RADAR_CHART_RENDERER_CATALOG_ENTRY,
    description: '用于多维指标对比、能力轮廓与综合评估的 ECharts 雷达图渲染器。',
    useCases: ['多维能力画像', '指标达成度对比', '多对象综合评估'],
    tags: ['chart', 'radar', 'echarts'],
    documentation: {
      summary: '雷达图渲染器用统一指标上限表达多个对象的多维能力轮廓，适合综合评估和目标差距展示。',
      usageGuide: '先在 indicators 中定义维度名称和 max，再确保每个 series.data 的顺序、长度与指标完全一致。多对象对比时使用不同名称和颜色。',
      limitations: '维度过多或名称过长会降低可读性；建议控制在 3 至 8 个指标，并保证所有序列使用相同量纲或已归一化数值。',
      notes: '默认案例以当前版本和目标版本对比五项工程指标，可直接观察能力差距。',
    },
    parameters: [
      { key: 'indicators', label: '雷达指标', type: 'RadarIndicator[]', control: 'json', required: true, defaultValue: radarChartPropsExample.indicators, description: '雷达轴名称与每个维度的最大值。' },
      { key: 'series', label: '雷达序列', type: 'RadarSeries[]', control: 'json', required: true, defaultValue: radarChartPropsExample.series, description: '各对比对象在雷达维度上的数值。' },
      { key: 'option', label: 'ECharts 扩展配置', type: 'EChartsOption', control: 'json', defaultValue: radarChartPropsExample.option, description: '与默认配置合并的可序列化 ECharts option。' },
      { key: 'height', label: '图表高度', type: 'number | string', control: 'number', defaultValue: radarChartPropsExample.height, description: '图表容器高度，数字以 px 计。' },
      { key: 'colors', label: '颜色列表', type: 'string[]', control: 'json', defaultValue: radarChartPropsExample.colors, description: '按序列顺序应用的颜色数组。' },
      { key: 'legend', label: '显示图例', type: 'boolean', control: 'boolean', defaultValue: radarChartPropsExample.legend, description: '是否显示图例。' },
      { key: 'loading', label: '加载状态', type: 'boolean', control: 'boolean', defaultValue: radarChartPropsExample.loading, description: '是否显示 ECharts 加载效果。' },
    ],
    events: [],
    examples: [
      {
        key: 'quality-profile',
        name: '版本质量画像',
        description: '当前版本与目标版本在五个工程指标上的雷达对比案例。',
        renderDocument: createRenderDocument({
          pageId: 'component-example-radar-chart',
          component: 'radar-chart-renderer',
          props: radarChartPropsExample,
          layout: { minHeight: '400px' },
        }),
      },
    ],
  },
  {
    ...PIE_CHART_RENDERER_CATALOG_ENTRY,
    description: '用于占比构成、来源分布和环形指标展示的 ECharts 饼图渲染器。',
    useCases: ['渠道来源占比', '业务构成分析', '环形 KPI 分布'],
    tags: ['chart', 'pie', 'echarts'],
    documentation: {
      summary: '饼图渲染器将名称和值映射为占比扇区，支持普通饼图与环形图，并沿用应用主题色和提示框样式。',
      usageGuide: 'data 使用 { name, value } 数组；需要中空环形效果时开启 donut。复杂但可序列化的 ECharts 配置放入 option 覆盖。',
      limitations: '分类过多会影响标签可读性，建议控制在 3 至 8 项；option 不能包含 formatter 函数或 ECharts 实例。',
      notes: '默认案例展示渠道来源分布，并开启环形样式。',
    },
    parameters: [
      { key: 'data', label: '占比数据', type: 'PieDataItem[]', control: 'json', required: true, defaultValue: pieChartPropsExample.data, description: '名称和值组成的扇区数据。' },
      { key: 'option', label: 'ECharts 扩展配置', type: 'EChartsOption', control: 'json', defaultValue: pieChartPropsExample.option, description: '与默认配置合并的可序列化 ECharts option。' },
      { key: 'height', label: '图表高度', type: 'number | string', control: 'number', defaultValue: pieChartPropsExample.height, description: '图表容器高度，数字以 px 计。' },
      { key: 'colors', label: '颜色列表', type: 'string[]', control: 'json', defaultValue: pieChartPropsExample.colors, description: '按数据顺序应用的颜色数组。' },
      { key: 'donut', label: '环形模式', type: 'boolean', control: 'boolean', defaultValue: pieChartPropsExample.donut, description: '是否将饼图显示为中空环形图。' },
      { key: 'legend', label: '显示图例', type: 'boolean', control: 'boolean', defaultValue: pieChartPropsExample.legend, description: '是否显示图例。' },
      { key: 'loading', label: '加载状态', type: 'boolean', control: 'boolean', defaultValue: pieChartPropsExample.loading, description: '是否显示 ECharts 加载效果。' },
    ],
    events: [],
    examples: [{
      key: 'traffic-source',
      name: '渠道来源分布',
      description: '按访问来源展示渠道占比的环形图案例。',
      renderDocument: createRenderDocument({
        pageId: 'component-example-pie-chart',
        component: 'pie-chart-renderer',
        props: pieChartPropsExample,
        layout: { minHeight: '360px' },
      }),
    }],
  },
  {
    ...BAR_CHART_RENDERER_CATALOG_ENTRY,
    description: '用于分类数值比较、横向排名和堆叠构成分析的 ECharts 柱状图渲染器。',
    useCases: ['分类排名', '季度指标比较', '堆叠结构分析'],
    tags: ['chart', 'bar', 'echarts'],
    documentation: {
      summary: '柱状图渲染器面向离散分类之间的量值比较，支持纵向、横向和堆叠模式。',
      usageGuide: 'categories 与 series.data 按相同顺序对齐；horizontal 切换横向条形图，stacked 开启默认堆叠，也可在单个序列中设置 stack。',
      limitations: '分类过多时应配合横向模式或截断标签；option 只能保存可序列化配置。',
      notes: '默认案例比较不同城市层级的两个季度数据。',
    },
    parameters: [
      { key: 'categories', label: 'X 轴分类', type: 'Array<string | number>', control: 'json', required: true, defaultValue: barChartPropsExample.categories, description: '横轴分类或排名维度。' },
      { key: 'series', label: '柱状序列', type: 'BarChartSeries[]', control: 'json', required: true, defaultValue: barChartPropsExample.series, description: '柱状序列及其数值、颜色和堆叠样式。' },
      { key: 'option', label: 'ECharts 扩展配置', type: 'EChartsOption', control: 'json', defaultValue: barChartPropsExample.option, description: '与默认配置合并的可序列化 ECharts option。' },
      { key: 'height', label: '图表高度', type: 'number | string', control: 'number', defaultValue: barChartPropsExample.height, description: '图表容器高度，数字以 px 计。' },
      { key: 'unit', label: '数值单位', type: 'string', control: 'text', defaultValue: barChartPropsExample.unit, description: '工具提示与 Y 轴展示的数值单位。' },
      { key: 'colors', label: '颜色列表', type: 'string[]', control: 'json', defaultValue: barChartPropsExample.colors, description: '按序列顺序应用的颜色数组。' },
      { key: 'legend', label: '显示图例', type: 'boolean', control: 'boolean', defaultValue: barChartPropsExample.legend, description: '是否显示图例。' },
      { key: 'horizontal', label: '横向模式', type: 'boolean', control: 'boolean', defaultValue: barChartPropsExample.horizontal, description: '是否切换为横向条形图。' },
      { key: 'stacked', label: '堆叠模式', type: 'boolean', control: 'boolean', defaultValue: barChartPropsExample.stacked, description: '是否将未显式指定 stack 的序列堆叠。' },
      { key: 'loading', label: '加载状态', type: 'boolean', control: 'boolean', defaultValue: barChartPropsExample.loading, description: '是否显示 ECharts 加载效果。' },
    ],
    events: [],
    examples: [{
      key: 'city-quarter-comparison',
      name: '城市层级季度比较',
      description: '比较不同城市层级两个季度业务量的柱状图案例。',
      renderDocument: createRenderDocument({
        pageId: 'component-example-bar-chart',
        component: 'bar-chart-renderer',
        props: barChartPropsExample,
        layout: { minHeight: '360px' },
      }),
    }],
  },
  {
    ...GAUGE_CHART_RENDERER_CATALOG_ENTRY,
    description: '用于单值进度、达成率和容量状态展示的 ECharts 仪表盘渲染器。',
    useCases: ['目标达成率', '系统容量监控', '单指标健康度'],
    tags: ['chart', 'gauge', 'echarts'],
    documentation: {
      summary: '仪表盘渲染器以 min、max 和 value 表达单一指标在区间中的当前位置，并支持主题化进度弧线。',
      usageGuide: '设置 value、min、max 定义数值区间，unit 仅影响中心详情文本；需要更细粒度刻度、分段或指针时使用 option。',
      limitations: '仪表盘适合单指标概览，不适合同时展示多个维度；必须确保 max 大于 min。',
      notes: '默认案例展示 78% 的目标达成率。',
    },
    parameters: [
      { key: 'value', label: '当前值', type: 'number', control: 'number', required: true, defaultValue: gaugeChartPropsExample.value, description: '仪表盘当前数值。' },
      { key: 'min', label: '最小值', type: 'number', control: 'number', defaultValue: gaugeChartPropsExample.min, description: '数值区间下限。' },
      { key: 'max', label: '最大值', type: 'number', control: 'number', defaultValue: gaugeChartPropsExample.max, description: '数值区间上限。' },
      { key: 'unit', label: '数值单位', type: 'string', control: 'text', defaultValue: gaugeChartPropsExample.unit, description: '中心详情文本后缀。' },
      { key: 'option', label: 'ECharts 扩展配置', type: 'EChartsOption', control: 'json', defaultValue: gaugeChartPropsExample.option, description: '与默认配置合并的可序列化 ECharts option。' },
      { key: 'height', label: '图表高度', type: 'number | string', control: 'number', defaultValue: gaugeChartPropsExample.height, description: '图表容器高度，数字以 px 计。' },
      { key: 'colors', label: '颜色列表', type: 'string[]', control: 'json', defaultValue: gaugeChartPropsExample.colors, description: '进度弧线使用的颜色数组。' },
      { key: 'loading', label: '加载状态', type: 'boolean', control: 'boolean', defaultValue: gaugeChartPropsExample.loading, description: '是否显示 ECharts 加载效果。' },
    ],
    events: [],
    examples: [{
      key: 'target-progress',
      name: '目标达成率',
      description: '展示单个目标完成进度的仪表盘案例。',
      renderDocument: createRenderDocument({
        pageId: 'component-example-gauge-chart',
        component: 'gauge-chart-renderer',
        props: gaugeChartPropsExample,
        layout: { minHeight: '320px' },
      }),
    }],
  },
  {
    ...FUNNEL_CHART_RENDERER_CATALOG_ENTRY,
    description: '用于转化漏斗、流程阶段和逐级流失分析的 ECharts 漏斗图渲染器。',
    useCases: ['营销转化漏斗', '流程阶段流失', '销售机会分层'],
    tags: ['chart', 'funnel', 'echarts'],
    documentation: {
      summary: '漏斗图渲染器按 value 从高到低展示流程阶段规模，帮助识别各阶段的转化和流失。',
      usageGuide: 'data 使用 { name, value } 数组，通常按流程顺序传入；组件默认按数值降序排列，可在 option 中覆盖 sort、标签和布局。',
      limitations: '漏斗图只适合有明确阶段顺序且数值单调递减的流程，不适合一般分类比较。',
      notes: '默认案例展示访问到复购的四阶段转化漏斗。',
    },
    parameters: [
      { key: 'data', label: '漏斗数据', type: 'PieDataItem[]', control: 'json', required: true, defaultValue: funnelChartPropsExample.data, description: '流程阶段名称和值。' },
      { key: 'option', label: 'ECharts 扩展配置', type: 'EChartsOption', control: 'json', defaultValue: funnelChartPropsExample.option, description: '与默认配置合并的可序列化 ECharts option。' },
      { key: 'height', label: '图表高度', type: 'number | string', control: 'number', defaultValue: funnelChartPropsExample.height, description: '图表容器高度，数字以 px 计。' },
      { key: 'colors', label: '颜色列表', type: 'string[]', control: 'json', defaultValue: funnelChartPropsExample.colors, description: '按阶段顺序应用的颜色数组。' },
      { key: 'legend', label: '显示图例', type: 'boolean', control: 'boolean', defaultValue: funnelChartPropsExample.legend, description: '是否显示图例。' },
      { key: 'loading', label: '加载状态', type: 'boolean', control: 'boolean', defaultValue: funnelChartPropsExample.loading, description: '是否显示 ECharts 加载效果。' },
    ],
    events: [],
    examples: [{
      key: 'conversion-funnel',
      name: '转化漏斗',
      description: '展示访问、提交、支付和复购阶段的漏斗图案例。',
      renderDocument: createRenderDocument({
        pageId: 'component-example-funnel-chart',
        component: 'funnel-chart-renderer',
        props: funnelChartPropsExample,
        layout: { minHeight: '360px' },
      }),
    }],
  },
  {
    ...SCATTER_CHART_RENDERER_CATALOG_ENTRY,
    description: '用于两个连续变量关系、聚类分布和异常点识别的 ECharts 散点图渲染器。',
    useCases: ['变量相关性分析', '客户分群', '异常点识别'],
    tags: ['chart', 'scatter', 'echarts'],
    documentation: {
      summary: '散点图渲染器在连续的 X、Y 坐标中绘制样本点，并支持多组序列和坐标轴名称。',
      usageGuide: 'series.data 使用 [x, y] 数组，也可以使用带 value 的对象；多组样本通过不同 series.name 区分。',
      limitations: '散点图需要数值坐标，文本分类值应在进入 Renderer 前完成编码或转换；option 不能包含运行时函数。',
      notes: '默认案例对比客户样本与重点客户的客单价和生命周期价值。',
    },
    parameters: [
      { key: 'series', label: '散点序列', type: 'ScatterSeries[]', control: 'json', required: true, defaultValue: scatterChartPropsExample.series, description: '散点序列和二维数值点。' },
      { key: 'option', label: 'ECharts 扩展配置', type: 'EChartsOption', control: 'json', defaultValue: scatterChartPropsExample.option, description: '与默认配置合并的可序列化 ECharts option。' },
      { key: 'height', label: '图表高度', type: 'number | string', control: 'number', defaultValue: scatterChartPropsExample.height, description: '图表容器高度，数字以 px 计。' },
      { key: 'colors', label: '颜色列表', type: 'string[]', control: 'json', defaultValue: scatterChartPropsExample.colors, description: '按序列顺序应用的颜色数组。' },
      { key: 'xName', label: 'X 轴名称', type: 'string', control: 'text', defaultValue: scatterChartPropsExample.xName, description: 'X 轴标题。' },
      { key: 'yName', label: 'Y 轴名称', type: 'string', control: 'text', defaultValue: scatterChartPropsExample.yName, description: 'Y 轴标题。' },
      { key: 'legend', label: '显示图例', type: 'boolean', control: 'boolean', defaultValue: scatterChartPropsExample.legend, description: '是否显示图例。' },
      { key: 'loading', label: '加载状态', type: 'boolean', control: 'boolean', defaultValue: scatterChartPropsExample.loading, description: '是否显示 ECharts 加载效果。' },
    ],
    events: [],
    examples: [{
      key: 'customer-value-distribution',
      name: '客户价值分布',
      description: '对比客户样本和重点客户价值分布的散点图案例。',
      renderDocument: createRenderDocument({
        pageId: 'component-example-scatter-chart',
        component: 'scatter-chart-renderer',
        props: scatterChartPropsExample,
        layout: { minHeight: '360px' },
      }),
    }],
  },
  {
    ...HEATMAP_CHART_RENDERER_CATALOG_ENTRY,
    description: '用于时间段密度、区域活跃度和二维矩阵强度展示的 ECharts 热力图渲染器。',
    useCases: ['星期时段活跃度', '区域密度分析', '二维指标矩阵'],
    tags: ['chart', 'heatmap', 'echarts'],
    documentation: {
      summary: '热力图渲染器将二维分类坐标与数值映射为颜色强度，支持应用主题色阶和数值图例。',
      usageGuide: 'xCategories、yCategories 定义两个分类轴，data 使用 [xIndex, yIndex, value]；需要自定义色阶或标签时放入 option。',
      limitations: 'data 的坐标索引必须对应两个分类数组；矩阵过大时应控制标签和单元格数量。',
      notes: '默认案例展示一周不同时段的活跃度矩阵。',
    },
    parameters: [
      { key: 'xCategories', label: 'X 轴分类', type: 'Array<string | number>', control: 'json', required: true, defaultValue: heatmapChartPropsExample.xCategories, description: '横向分类数组。' },
      { key: 'yCategories', label: 'Y 轴分类', type: 'Array<string | number>', control: 'json', required: true, defaultValue: heatmapChartPropsExample.yCategories, description: '纵向分类数组。' },
      { key: 'data', label: '热力数据', type: 'HeatmapPoint[]', control: 'json', required: true, defaultValue: heatmapChartPropsExample.data, description: '由 X 索引、Y 索引和值组成的热力点。' },
      { key: 'option', label: 'ECharts 扩展配置', type: 'EChartsOption', control: 'json', defaultValue: heatmapChartPropsExample.option, description: '与默认配置合并的可序列化 ECharts option。' },
      { key: 'height', label: '图表高度', type: 'number | string', control: 'number', defaultValue: heatmapChartPropsExample.height, description: '图表容器高度，数字以 px 计。' },
      { key: 'colors', label: '颜色列表', type: 'string[]', control: 'json', defaultValue: heatmapChartPropsExample.colors, description: '由低到高的色阶颜色数组。' },
      { key: 'loading', label: '加载状态', type: 'boolean', control: 'boolean', defaultValue: heatmapChartPropsExample.loading, description: '是否显示 ECharts 加载效果。' },
    ],
    events: [],
    examples: [{
      key: 'weekly-activity',
      name: '周活跃度热力图',
      description: '展示一周各时段活跃度强弱的二维热力图案例。',
      renderDocument: createRenderDocument({
        pageId: 'component-example-heatmap-chart',
        component: 'heatmap-chart-renderer',
        props: heatmapChartPropsExample,
        layout: { minHeight: '360px' },
      }),
    }],
  },
]

export function findApplicationComponent(componentKey?: string) {
  return APPLICATION_COMPONENT_MANIFEST.find(item => item.key === componentKey)
}
