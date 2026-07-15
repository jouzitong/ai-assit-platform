# Components

`src/components/` 按能力域分层组织，避免继续把所有通用组件平铺在根目录。

目录约定：

- `basic/`：基础展示能力，如按钮、图标、标签。
- `canvas/`：流程画布、看板画布等可拖拽编排类组件。
- `input/`：输入、选择、日期、开关类组件。
- `layout/`：页面头部、动作栏、弹窗内容、表单栅格、label-value 等不含业务逻辑的布局原语。
- `data/`：表格、树、分页等数据展示组件。
- `feedback/`：弹窗、抽屉、空态、提示等交互反馈组件。
- `navigation/`：Tabs、菜单、面包屑等导航组件。

布局组件约定：

- `layout/` 只负责 DOM 编排、响应式折行和视觉 token 消费，不请求数据、不维护页面级业务状态。
- 页面模块和 `application` renderer 都可以复用 `layout/`；只有需要 schema/registry/runtime 参与动态解析的容器才放入 `src/application/layout/`。
- 新增表单字段布局优先组合 `LayoutFormGrid`、`LayoutFormGridItem` 和 `LayoutLabelValue`，不要在页面内重复写 label-value 与断点规则。

响应式容器约定：

> 完整规则以 [`docs/dev-spec/detail/frontend/responsive-theme.md`](../../../docs/dev-spec/detail/frontend/responsive-theme.md) 为准；此处只保留组件目录的执行摘要。

- 通用组件必须以父容器为尺寸来源，不使用 `100vw`、`100vh` 或 `window.innerWidth` 决定内部布局。
- 根节点默认支持 `width: 100%`、`max-width: 100%`，Flex/Grid 子节点必须允许 `min-width: 0`、`min-height: 0`。
- 组件实例不单独计算缩放；连续缩放统一由 Application 层的 `ResponsiveViewport` 提供。
- 结构变化使用 Container Query，不使用浏览器宽度 Media Query 推断组件可用空间。
- 拖拽、缩放等坐标交互必须通过响应式上下文把屏幕像素换算成逻辑像素。
- Select、Popover、DatePicker 等浮层优先挂载到响应式上下文提供的 overlay target，避免脱离缩放容器。
- 全局缩放边界和预设统一维护在 `src/config/responsive.ts`；页面或画布宿主通过 `preset` 选择场景，通过 `config` 做局部覆盖。
- 配置优先级为“内置默认 < 全局默认 < 预设继承链 < 当前预设 < 局部覆盖”，公共组件不读取或修改这些配置。
- 小于最小缩放比例后，交互场景通过滚动访问完整内容；预览场景可以裁剪，但不能继续无限缩小。

兼容策略：

- 历史组件入口暂时保留在 `src/components/` 根层，避免直接断引用。
- 新增组件优先落到对应分类目录，不再继续平铺。
