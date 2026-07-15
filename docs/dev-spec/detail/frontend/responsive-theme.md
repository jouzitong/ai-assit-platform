# 前端主题与容器响应式开发规范

## 1. 目标与适用范围

本规范用于统一 `ai-conversation-ui` 的主题 Token、容器响应式布局和有边界的等比例缩放能力，重点约束：

- `ai-conversation-ui/src/application/`
- `ai-conversation-ui/src/components/`
- 组合上述能力的页面、画布和 Render JSON 运行时宿主

本规范解决以下问题：

- 颜色、字号、行高、间距、圆角、阴影和常用尺寸可以全局调整，也可以按局部容器覆盖。
- 组件根据自身父容器变化，不把浏览器视口宽度误认为组件可用宽度。
- 需要展示整体画面的场景，在配置区间内连续等比例缩放，并在到达最小或最大边界后停止缩放。
- 全局默认、场景预设、模块扩展和宿主局部配置具有稳定、可追踪的覆盖顺序。

规范中的“必须”“禁止”是合并前必须满足的要求；“应该”是默认选择，偏离时需要在代码评审中说明原因；“可以”表示按场景选择。

## 2. 核心原则

1. **语义 Token 优先**：可复用的视觉值只有一个公共来源，组件消费语义，不复制具体数值。
2. **容器决定布局**：通用组件只根据父容器适配，不根据浏览器视口猜测可用空间。
3. **自然布局与整体缩放分工明确**：普通页面优先使用 Flex、Grid 和 Container Query；只有需要保留整体比例和全貌的画布、看板、预览等场景才使用 `ResponsiveViewport`。
4. **缩放必须有边界**：连续缩放始终受 `minScale` 和 `maxScale` 约束，不能无限缩小或放大。
5. **全局配置、局部覆盖**：公共规则进入全局配置，场景差异进入预设，单个宿主只覆盖真正的局部差异。
6. **一个渲染树一个缩放宿主**：子组件继承缩放上下文，不重复测量和二次缩放。
7. **交互与视觉保持同一坐标系**：弹层、拖拽、缩放和指针坐标必须感知当前缩放上下文。

## 3. 分层与职责

| 层级 | 唯一职责 | 允许做什么 | 禁止做什么 |
| --- | --- | --- | --- |
| `src/styles/variables.scss` | 公共主题源 | 定义 `--app-*` Token、明暗主题值和 Element Plus 全局映射 | 页面业务逻辑、单页临时值 |
| `src/config/responsive.ts` | 响应式配置源 | 定义内置默认、全局预设、继承、合并和校验 | 读取 DOM、测量容器、写页面业务判断 |
| `ResponsiveViewport.vue` | 连续缩放宿主 | 测量宿主、计算缩放、提供 scale 和 overlay 上下文 | 读取 Render JSON 业务字段、替子组件决定布局结构 |
| 页面、画布、Runtime 宿主 | 场景选择 | 选择 `preset`，必要时提供局部 `config` 或模块 `options` | 自行复制缩放公式、修改全局配置对象 |
| Renderer、公共组件 | 展示与局部交互 | 消费 Token、Container Query 和已有响应式上下文 | 读取浏览器宽度、创建嵌套缩放宿主、修改缩放配置 |
| Render JSON | 稳定声明 | 确有持久化需要时保存稳定的预设 key | 保存容器测量值、`rawScale`、`scale` 或可执行配置 |

## 4. 主题 Token 规范

### 4.1 唯一主题入口

- `src/styles/variables.scss` 是 `src/application` 和 `src/components` 的公共主题入口。
- 字体、字号、行高、颜色、间距、圆角、阴影、控件高度和可复用宽高必须优先使用 `--app-*` Token。
- 同一语义只允许有一个全局 Token。新增 Token 前必须先检索现有变量，禁止用近义名称复制相同能力。
- Token 必须按用途命名，例如 `--app-text-muted`、`--app-space-4`、`--app-control-height-md`；禁止用 `--gray-500`、`--size-14` 这类只描述当前字面值的名称作为业务语义 Token。
- 明暗主题通过 `:root` 与 `:root[data-theme='dark']` 提供同名 Token 的不同值，组件不得用主题判断分支维护两套样式。

### 4.2 Token 分类与使用边界

| 类型 | 规则 | 示例 |
| --- | --- | --- |
| 全局语义 Token | 多个组件共享，或主题切换时需要统一变化，必须放在 `variables.scss` | `--app-title`、`--app-radius-xl` |
| 组件私有别名 | 只用于组件内部表达语义，可以在组件根节点定义，但默认值必须映射公共 Token | `--app-pagination-surface: var(--app-surface-muted)` |
| 运行时动态值 | 由 props、测量或数据计算，可以通过 style/CSS 变量传入，不进入全局主题 | 画布坐标、滚动缩略图高度、编辑器受控高度 |
| 结构阈值 | Container Query 的断点和算法参考尺寸属于布局配置，不属于视觉主题 | `@container (max-width: 560px)`、`referenceSize.width` |

以下情况允许保留局部固定值，但不得伪装成公共主题：

- `0`、`100%`、`auto`、`none`、`Number.EPSILON` 等 CSS 或算法基础值。
- 画布坐标、图形尺寸、数据驱动颜色等运行时输入。
- `1px` 结构线、transform 原点、层级和 Container Query 阈值等局部结构值。
- 第三方库明确要求的协议值。

如果一个局部固定值在第二个组件中再次出现，或产品要求它随主题统一调整，必须提升为公共 Token。

### 4.3 Element Plus 与第三方渲染

- Element Plus 的全局 `--el-*` 变量必须在 `variables.scss` 中映射到 `--app-*` Token。
- 公共组件优先消费 `--app-*`。只有 Element Plus 适配层或局部控件确有私有语义时，才可以在拥有该控件的容器上窄范围覆盖 `--el-*`。
- 使用 `:deep(...)` 覆盖 Element Plus 内部样式时，选择器必须由当前组件根节点约束，禁止无边界全局覆盖。
- ECharts、CodeMirror、Canvas 等不能直接解析 CSS 变量的库，由公共宿主读取计算后的 Token；`data-theme` 变化后必须刷新对应渲染实例。
- 颜色不得作为状态的唯一表达方式，错误、成功、禁用等状态还必须具有文字、图标、形状或语义属性。

### 4.4 局部主题覆盖

- 页面或模块的局部风格通过父容器覆盖已有 CSS 变量实现，子组件自动继承。
- 局部覆盖只能修改差异 Token，禁止复制整套 `:root` 变量。
- 可复用的新语义应先补充全局 Token；只属于单一业务页面的装饰值保留在页面层，不进入公共组件。
- 禁止在组件 props 中增加大量 `color`、`fontSize`、`gap` 参数来替代主题系统。只有数据可视化颜色、受控尺寸等确实属于组件数据契约的值才可以通过 props 传入。

## 5. 容器响应式布局规范

### 5.1 尺寸来源

- `src/application` 和 `src/components` 下的通用实现必须以父容器为尺寸来源。
- 禁止使用 `window.innerWidth`、`window.innerHeight`、`screen.width`、`100vw` 或 `100vh` 决定组件内部布局。
- 组件根节点默认应支持 `width: 100%`、`max-width: 100%`；位于 Flex/Grid 中的可收缩节点必须设置适当的 `min-width: 0`、`min-height: 0`。
- DOM 尺寸监听统一使用 `ResizeObserver`，且只应存在于真正负责测量的宿主或基础能力中；普通子组件不重复监听同一容器。

### 5.2 结构变化

- 组件内部的列数、折行、操作栏收纳、侧栏隐藏等结构变化必须优先使用 Container Query。
- 提供查询上下文的拥有者设置 `container-type: inline-size` 或 `container-type: size`；子组件使用 `@container` 响应实际可用空间。
- 通用组件禁止用普通宽度 Media Query 表达内部布局。`prefers-reduced-motion`、打印、对比度等浏览器或用户偏好查询不受此限制。
- 只有最外层应用壳确实需要响应浏览器窗口时才可以使用视口 Media Query，不得把这种规则下沉到公共组件。
- Container Query 断点表达“布局在此宽度无法稳定工作”的结构阈值，不要求进入主题 Token，但相同布局模式应复用相同的容器命名和阈值。

### 5.3 自然响应与等比例缩放的选择

优先使用自然响应式布局的场景：

- CRUD 页面、普通表单、列表、详情页。
- 内容可以重排、折行、分页、滚动或收纳。
- 字号和交互尺寸需要保持正常可读性。

使用 `ResponsiveViewport` 的场景：

- 看板、画布、预览、投屏、Render JSON 整体渲染。
- 容器变小时仍需在一定范围内保留完整构图和相对比例。
- 字号、间距、模块宽高需要随整体画布一起连续缩放。

禁止为了省去响应式布局设计而给所有页面套整体缩放。自然响应式负责“重新排布”，`ResponsiveViewport` 负责“保持比例”，两者可以在同一宿主中协作，但职责不能混淆。

## 6. 有边界的等比例缩放

### 6.1 计算模型

设宿主容器尺寸为 `containerWidth × containerHeight`，逻辑参考尺寸为 `referenceWidth × referenceHeight`：

```text
widthRatio  = containerWidth  / referenceWidth
heightRatio = containerHeight / referenceHeight

contain -> rawScale = min(widthRatio, heightRatio)
cover   -> rawScale = max(widthRatio, heightRatio)
width   -> rawScale = widthRatio
height  -> rawScale = heightRatio

scale = clamp(rawScale, minScale, maxScale)
```

- `rawScale` 只表示容器原始可用比例。
- `scale` 是组件实际使用的比例，必须被限制在 `[minScale, maxScale]` 内。
- 默认需要展示完整画面时使用 `contain`；`cover` 只用于明确允许裁剪的展示场景。
- 容器尺寸无效或尚未测量时不得使用无穷值或负值参与渲染。

### 6.2 配置字段

| 字段 | 含义 | 规范要求 |
| --- | --- | --- |
| `referenceSize.width` | 逻辑画布参考宽度 | 必须大于 `0`，按真实设计基准设置 |
| `referenceSize.height` | 逻辑画布参考高度 | 必须大于 `0`，按真实设计基准设置 |
| `minScale` | 最小缩放比例 | 面向用户的场景必须大于 `0`；交互场景不得为展示全貌而盲目降低 |
| `maxScale` | 最大缩放比例 | 必须大于 `0` 且不小于 `minScale` |
| `fit` | 比例计算方式 | `contain`、`cover`、`width`、`height` |
| `underflow` | 低于最小比例后的行为 | `scroll` 或 `clip` |
| `align` | 画布在宿主中的对齐 | `center` 或 `start` |

### 6.3 下限与上限行为

- 当 `rawScale < minScale` 时，必须停止缩小并保持 `scale = minScale`。
- 交互场景默认使用 `underflow: 'scroll'`，通过滚动访问完整逻辑画布，保证文字和点击目标不会继续缩小。
- 纯预览且明确不需要交互时可以使用 `underflow: 'clip'`；选择裁剪必须由预设显式表达，不能成为公共默认值。
- 当 `rawScale > maxScale` 时，必须停止放大并保持 `scale = maxScale`，多余空间按 `align` 处理。
- 在 `[minScale, maxScale]` 区间内，字号、间距、边框、模块宽高和内部组件随逻辑画布整体连续缩放，不允许子组件再做第二次比例变换。

## 7. 全局配置、预设与局部扩展

### 7.1 唯一配置源

- 内置默认和全局预设统一维护在 `src/config/responsive.ts`。
- `RESPONSIVE_VIEWPORT_GLOBAL_OPTIONS` 是工程级配置入口，不允许页面在运行时直接修改。
- `ResponsiveViewport` 通过 `preset` 选择场景，通过 `config` 覆盖当前宿主差异，通过 `options` 接收模块级扩展配置。

固定合并顺序为：

```text
内置默认值
  < 全局 defaults
  < 父预设
  < 当前预设
  < 当前宿主 config
```

- 后层只覆盖已声明字段；`referenceSize.width` 和 `referenceSize.height` 必须深度合并。
- 预设继承必须检测循环；未知预设必须回退到有效默认预设并在开发环境提示。
- 无效数值必须由统一解析器兜底，组件不得各自实现一套修正逻辑。

### 7.2 配置层级选择

| 需求 | 放置位置 |
| --- | --- |
| 全项目的默认参考尺寸或边界 | 全局 `defaults` |
| 多个页面共享的看板、交互、预览场景 | 全局 `presets` |
| 一个业务模块共享且不适合全局公开的场景 | `extendResponsiveViewportOptions(...)` |
| 单个宿主的特殊尺寸差异 | 宿主 `config` |

- 同一种局部 `config` 在两个及以上宿主重复出现时，应该提取为有语义的预设。
- 预设 key 使用稳定的英文语义名称。key 一旦进入 Render JSON 或持久化数据，重命名必须提供兼容迁移。
- 模块扩展必须基于现有 options 创建新对象，禁止修改、覆盖或删除其他模块的全局预设。
- 局部配置只覆盖差异值，禁止把解析后的完整配置复制到页面中。

### 7.3 Render JSON 边界

- Render JSON 只有在场景确实需要持久化时才保存稳定的 `preset` key。
- `referenceSize` 等稳定设计参数只有在协议明确允许用户配置时才可保存。
- 禁止保存 `containerWidth`、`containerHeight`、`rawScale`、`scale`、overlay DOM 或其他运行时对象。
- Runtime 和 Renderer 不读取浏览器视口计算缩放，页面、画布或 Runtime 外层宿主负责组合 `ResponsiveViewport`。

## 8. 缩放上下文与交互

### 8.1 单宿主规则

- 同一渲染树只能有一个负责连续缩放的 `ResponsiveViewport`。
- 页面、画布或 Runtime 外层宿主负责创建它；Renderer 和公共组件只通过 `useResponsiveViewport()` 消费上下文。
- 公共组件不得为了支持独立预览而在内部隐式包裹 `ResponsiveViewport`。无上下文时按 `scale = 1` 正常工作。
- 如果确实需要内嵌第二个独立画布，必须将它视为隔离的渲染树，明确容器边界，并验证父子 transform 不会造成双重坐标换算。

### 8.2 浮层

- Select、Popover、Tooltip、DatePicker、TimePicker、Dialog 等浮层必须优先使用 `useResponsiveOverlayTarget()` 获取当前 overlay target。
- Element Plus 组件应通过 `append-to`、`teleported` 等能力让浮层留在缩放上下文中；禁止在存在响应式上下文时无条件挂载到 `body`。
- 无响应式上下文时可以使用 Element Plus 默认挂载行为，保证公共组件可独立复用。
- 浮层的层级、定位、点击区域、关闭行为和明暗主题都必须在缩放后验证。

### 8.3 坐标与尺寸换算

- 拖拽、框选、缩放手柄等基于指针的交互必须把屏幕像素换算为逻辑像素。
- 统一通过 `useResponsiveInteractionScale()` 获取有效比例，核心换算为：

```text
logicalDelta = screenDelta / scale
```

- 组件不得读取 CSS transform 字符串后自行解析比例，也不得使用另一个局部 scale。
- 滚动距离、命中测试、吸附阈值属于屏幕像素还是逻辑像素，必须在实现中明确，禁止混用。

## 9. 可访问性与体验下限

- 交互预设的 `minScale` 必须同时考虑最小可读字号和最小点击目标，不能只以“塞进容器”为目标。
- 达到最小缩放后优先滚动、折叠或切换自然布局，禁止继续压缩到无法阅读或无法点击。
- 缩放不能破坏键盘焦点顺序、焦点可见性、Tooltip/Popover 定位和屏幕阅读器语义。
- 动效必须支持 `@media (prefers-reduced-motion: reduce)`，缩放宿主不强制为尺寸变化增加动画。
- 明暗主题下都必须保持可读对比度，状态不能只通过颜色区分。

## 10. 禁止模式

以下实现不得合并：

- 在组件中用 `window.innerWidth` 或 `100vw` 判断自身布局。
- 在多个 Renderer 内复制 `ResizeObserver + scale` 计算逻辑。
- 通过大量组件 props 逐级传递颜色、字号和间距，形成第二套主题系统。
- 在页面中复制完整全局预设，只修改其中一个字段。
- 低于 `minScale` 后继续缩小，或高于 `maxScale` 后继续放大。
- 在已经缩放的 Renderer 内再次使用 `transform: scale(...)` 实现整体适配。
- 浮层无条件挂载到 `body`，导致位置、尺寸或主题脱离缩放容器。
- 把运行时测量结果写入 Render JSON、Store 或后端持久化配置。
- 为修复单个页面而直接覆盖全局 `--el-*`，影响其他 Element Plus 组件。

## 11. 开发与评审流程

新增或修改 `src/application`、`src/components` 中的视觉和布局能力时，按以下顺序执行：

1. 判断需求属于主题 Token、自然容器响应、整体缩放，或三者组合。
2. 检索现有 `--app-*` Token、布局组件和响应式预设，优先复用。
3. 决定配置层级：全局默认、全局预设、模块扩展或宿主局部覆盖。
4. 确认渲染树中由谁创建唯一 `ResponsiveViewport`。
5. 实现 Container Query、overlay target 和坐标换算等必要适配。
6. 按第 12 节矩阵验证，并执行 `cd ai-conversation-ui && npm run build`。

代码评审必须回答：

- 是否新增了可复用视觉固定值？为什么不使用现有 Token？
- 组件的尺寸来源是父容器还是浏览器视口？
- 该场景为什么需要整体缩放，而不是自然响应式重排？
- `minScale`、`maxScale`、`underflow` 的用户体验依据是什么？
- 是否存在嵌套缩放、浮层逃逸或屏幕坐标未换算？
- 局部配置是否已经重复到应该抽成预设？

## 12. 验收矩阵

每个新增或修改的缩放宿主至少验证以下状态：

| 场景 | 必须确认 |
| --- | --- |
| 参考尺寸 | `scale = 1` 时布局、字号、间距符合设计基准 |
| 区间内缩小 | 画面完整，比例连续变化，无二次缩放 |
| 小于最小边界 | 停在 `minScale`，`scroll` 或 `clip` 行为符合预设 |
| 区间内放大 | 画面按比例放大，浮层和交互位置正确 |
| 大于最大边界 | 停在 `maxScale`，剩余空间和对齐符合预期 |
| 极窄、极矮容器 | `fit` 语义正确，没有错误使用视口断点 |
| 明暗主题 | Token、Element Plus、Canvas 和浮层同步切换 |
| 交互 | 拖拽、框选、缩放、滚动、键盘焦点使用正确坐标系 |
| 独立使用 | 公共组件在没有响应式上下文时按比例 `1` 正常工作 |
| 构建 | `npm run build` 通过，无新增编译错误 |

## 13. 静态审查与例外管理

在自动检查脚本接入前，评审者应对 `src/application` 和 `src/components` 做以下定向审查：

- 新增的十六进制色、`rgb/rgba`、渐变、字体大小、间距和常用宽高是否应该进入 Token。
- 是否新增 `window.innerWidth`、`window.innerHeight`、`100vw`、`100vh` 或普通宽度 Media Query。
- 是否新增 `teleport`、`teleported`、`append-to`，并正确处理响应式 overlay target。
- 是否新增 `transform: scale(...)`、`ResizeObserver` 或指针坐标计算，并遵守单宿主与逻辑像素规则。

允许的例外必须同时满足：

1. 属于第 4.2 节允许的运行时值、结构值或第三方协议值。
2. 不会形成新的主题或缩放配置来源。
3. 在代码附近用简短注释说明原因，或在评审记录中明确说明。
4. 相同例外再次出现时重新评估是否应抽成 Token、公共能力或预设。

