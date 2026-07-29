import {
  COMBO_CHART_RENDERER_CATALOG_ENTRY,
  FORM_RENDERER_CATALOG_ENTRY,
  LINE_CHART_RENDERER_CATALOG_ENTRY,
  LIST_RENDERER_CATALOG_ENTRY,
  RADAR_CHART_RENDERER_CATALOG_ENTRY,
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
    { key: 'keyword', label: '任务名称', component: 'zg-input', placeholder: '输入名称后回车搜索' },
    {
      key: 'status',
      label: '状态',
      component: 'zg-selector',
      list: [
        { key: '待处理', value: 'pending' },
        { key: '进行中', value: 'running' },
        { key: '已完成', value: 'completed' },
      ],
      options: { clearable: true, submitOnChange: true },
    },
  ],
  fields: [
    { key: 'id', name: 'id', label: '任务编号', field: ['id'], options: { styles: { width: 140 } } },
    { key: 'name', name: 'name', label: '任务名称', field: ['name'] },
    { key: 'owner', name: 'owner', label: '负责人', field: ['owner'] },
    { key: 'status', name: 'status', label: '状态', field: ['status'] },
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
      options: { required: true, placeholder: '请输入资产名称' },
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
      options: { clearable: true },
    },
    {
      key: 'description',
      name: 'description',
      label: '能力说明',
      component: 'zg-input',
      type: 'textarea',
      options: { rows: 4, placeholder: '说明组件能力和使用边界' },
    },
    {
      key: 'enabled',
      name: 'enabled',
      label: '启用状态',
      type: 'switch',
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

export const APPLICATION_COMPONENT_MANIFEST: ApplicationComponentDefinition[] = [
  {
    ...LIST_RENDERER_CATALOG_ENTRY,
    description: '基于 Schema 组装标题、页签、树、筛选、摘要、数据列表与分页的通用业务列表容器。',
    useCases: ['后台管理列表', '带树分类的数据浏览', '由 Render JSON 动态驱动的列表页'],
    tags: ['list', 'schema', 'renderer'],
    documentation: {
      summary: '通用列表渲染器将页面标题、动作、页签、左侧树、筛选器、摘要卡片、数据表格和分页统一收敛到声明式 Schema 中。',
      usageGuide: '先在 schema 中声明字段、筛选项、动作和分页策略，再通过 records、treeData 与 total 传入受控数据。actions 与 actionColumns 的每一项使用 { key, name, action, options? }；options 可省略，只承载 type、style、class、icon。type 支持 default、primary、success、warning、danger、info，icon 支持 download、fullscreen、operation、print、refresh。筛选、页签和分页变化通过 queryChange/reload 事件交给 Runtime 或页面服务重新取数。',
      limitations: 'Renderer 不直接请求后端，也不执行 CREATE、VIEW、DELETE 等业务动作；外部必须监听语义事件并负责权限、请求、跳转和错误处理。',
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
      usageGuide: '在 schema.fields 中声明字段控件，在 schema.groups 中组织表单分区，通过 modelValue 传入初始值并监听 update:modelValue/change。schema.actions 的每一项使用 { key, name, action, options? }；options 可省略，只承载 type、style、class、icon。type 支持 default、primary、success、warning、danger、info，icon 支持 download、fullscreen、operation、print、refresh。保存、重置等动作由 action 事件交给上层处理。',
      limitations: '当前字段控件以已注册的 Application 输入组件为准；Renderer 不负责远程选项加载、表单提交、权限判断和服务端校验。',
      notes: '示例覆盖文本、选择、长文本、开关、双分组和顶部动作，适合作为新增或编辑类页面的基础模板。',
    },
    parameters: [
      { key: 'schema', label: '表单 Schema', type: 'FormRendererSchema', control: 'json', required: true, defaultValue: formSchemaExample, description: '定义字段、分组、动作、关系与表单布局；动作只使用 { key, name, action, options? }。' },
      { key: 'modelValue', label: '表单数据', type: 'Record<string, unknown>', control: 'json', defaultValue: formModelValueExample, description: '表单受控数据对象。' },
      { key: 'readonly', label: '只读模式', type: 'boolean', control: 'boolean', defaultValue: false, description: '开启后所有字段仅展示不可编辑。' },
    ],
    events: [
      { name: 'action', description: '表单操作按钮触发时输出。' },
      { name: 'change', description: '字段变化时输出字段、当前值与全量数据。' },
      { name: 'update:modelValue', description: '表单受控值更新时输出。' },
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
]

export function findApplicationComponent(componentKey?: string) {
  return APPLICATION_COMPONENT_MANIFEST.find(item => item.key === componentKey)
}
