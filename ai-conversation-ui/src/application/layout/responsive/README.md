# ResponsiveViewport

`ResponsiveViewport` 以参考尺寸为逻辑画布，根据宿主容器计算连续缩放比例。字号、间距、宽高和子组件会随画布整体缩放；小于最小比例后停止缩小。

主题 Token、适用场景、配置层级、单宿主、浮层和验收规则以 [`docs/dev-spec/detail/frontend/responsive-theme.md`](../../../../../docs/dev-spec/detail/frontend/responsive-theme.md) 为准；本文只说明组件 API 和扩展示例。

## 全局预设

全局默认和内置预设位于 `src/config/responsive.ts`：

- `standard`：通用场景，缩放范围 `0.5 ~ 1.4`。
- `interactive`：交互场景，最小比例 `0.75`，低于下限后滚动。
- `preview`：纯预览场景，缩放范围 `0.45 ~ 1`，低于下限后裁剪。
- `dashboard`：继承 `interactive`，参考尺寸为 `1200 x 720`。

## 局部覆盖

```vue
<ResponsiveViewport
  preset="dashboard"
  :config="{
    minScale: 0.6,
    maxScale: 1.15,
    referenceSize: { width: 1080 },
  }"
>
  <ApplicationRenderer />
</ResponsiveViewport>
```

局部 `config` 只覆盖已声明字段，`referenceSize` 按 `width/height` 深度合并。

## 扩展预设

```ts
import {
  extendResponsiveViewportOptions,
  RESPONSIVE_VIEWPORT_GLOBAL_OPTIONS,
} from '../../../config/responsive'

export const moduleResponsiveOptions = extendResponsiveViewportOptions(
  RESPONSIVE_VIEWPORT_GLOBAL_OPTIONS,
  {
    presets: {
      compactDashboard: {
        extends: 'dashboard',
        config: { minScale: 0.65 },
      },
    },
  },
)
```

扩展配置通过 `ResponsiveViewport` 的 `options` 属性传入。预设继承会检测循环，未知预设会回退到默认预设并输出警告。
