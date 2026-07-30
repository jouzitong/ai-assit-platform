# Layout and static Render nodes

## Contents

- Availability rule
- Shared layout fields
- Layout components
- Static nodes
- Composition example

## Availability rule

The frontend runtime recognizes the keys in this reference. For the current Agent phase, this skill is the authoritative source for these layouts and static nodes, and all documented entries use component version `1.0.0`. Do not emit undocumented aliases or components.

## Shared layout fields

Layout containers consume these node `layout` fields:

- `gridTemplateColumns`, `gridTemplateRows`, `gridAutoRows`
- `gap`, `padding`
- `alignItems`, `justifyItems`, `justifyContent`
- `width`, `height`, `minHeight`
- `direction: "row" | "column"` and `wrap` for stack layouts
- `columns`, `rows`, and `showGrid` for the grid developer overlay

Child nodes can use `gridColumn`, `gridRow`, `width`, `height`, and `minHeight` in their own `layout` object.

## Layout components

### `zg-page-layout`

Alias: `page`. Use as the outer natural-flow page container.

```json
{
  "id": "page-root",
  "component": "zg-page-layout",
  "componentVersion": "1.0.0",
  "layout": { "gap": "24px", "padding": "24px", "minHeight": "100%" },
  "children": []
}
```

### `zg-container-layout`

Alias: `container`. Use for a neutral nested vertical container without section decoration.

```json
{
  "id": "content-container",
  "component": "zg-container-layout",
  "componentVersion": "1.0.0",
  "layout": { "gap": "16px", "width": "100%" },
  "children": []
}
```

### `zg-section-layout`

Alias: `section`. Use for a bordered, raised content section.

```json
{
  "id": "summary-section",
  "component": "zg-section-layout",
  "componentVersion": "1.0.0",
  "layout": { "gap": "16px" },
  "children": []
}
```

### `zg-stack-layout`

Alias: `stack`. Use for row or column flow; enable `wrap` for responsive wrapping.

```json
{
  "id": "action-stack",
  "component": "zg-stack-layout",
  "componentVersion": "1.0.0",
  "layout": { "direction": "row", "wrap": true, "gap": "12px", "alignItems": "center" },
  "children": []
}
```

### `zg-grid-layout`

Alias: `grid`. Use for dashboard grids. If `gridTemplateColumns` is absent, the runtime uses an auto-fit grid.

```json
{
  "id": "dashboard-grid",
  "component": "zg-grid-layout",
  "componentVersion": "1.0.0",
  "layout": {
    "gridTemplateColumns": "repeat(2, minmax(0, 1fr))",
    "gridAutoRows": "minmax(320px, auto)",
    "gap": "16px"
  },
  "children": []
}
```

### `zg-split-layout`

Alias: `split`. Use for two-column master/detail content. The runtime defaults to a 1:2 column ratio and collapses to one column in narrow containers.

```json
{
  "id": "master-detail",
  "component": "zg-split-layout",
  "componentVersion": "1.0.0",
  "layout": { "gridTemplateColumns": "minmax(240px, 1fr) minmax(0, 2fr)", "gap": "20px" },
  "children": []
}
```

### `zg-sheet-layout`

Alias: `sheet`. Use for report-like printable sheets with a solid surface and print-friendly decoration removal.

```json
{
  "id": "report-sheet",
  "component": "zg-sheet-layout",
  "componentVersion": "1.0.0",
  "layout": { "gap": "20px", "minHeight": "720px" },
  "children": []
}
```

## Static nodes

### `heading`

Alias: `title`. Render a level-two heading. The runtime reads `props.value`, then `props.text`, then `props.title`.

```json
{
  "id": "page-heading",
  "component": "heading",
  "componentVersion": "1.0.0",
  "props": { "text": "经营分析" }
}
```

### `text`

Render plain paragraph text. The runtime reads `props.value`, then `props.text`, then `props.title`.

```json
{
  "id": "page-description",
  "component": "text",
  "componentVersion": "1.0.0",
  "props": { "text": "展示已通过数据预览验证的核心指标。" }
}
```

## Composition example

Use the exact `1.0.0` versions documented by this skill snapshot.

```json
{
  "id": "dashboard-root",
  "component": "zg-page-layout",
  "componentVersion": "1.0.0",
  "layout": { "gap": "20px", "padding": "24px" },
  "children": [
    {
      "id": "dashboard-title",
      "component": "heading",
      "componentVersion": "1.0.0",
      "props": { "text": "经营分析" }
    },
    {
      "id": "dashboard-grid",
      "component": "zg-grid-layout",
      "componentVersion": "1.0.0",
      "layout": { "gridTemplateColumns": "repeat(2, minmax(0, 1fr))", "gap": "16px" },
      "children": [
        {
          "id": "trend-chart",
          "component": "line-chart-renderer",
          "componentVersion": "1.0.0",
          "props": {
            "categories": ["1月", "2月", "3月"],
            "series": [{ "name": "成交额", "data": [128, 176, 204] }],
            "height": 320
          }
        },
        {
          "id": "quality-chart",
          "component": "radar-chart-renderer",
          "componentVersion": "1.0.0",
          "props": {
            "indicators": [
              { "name": "稳定性", "max": 100 },
              { "name": "性能", "max": 100 },
              { "name": "安全性", "max": 100 }
            ],
            "series": [{ "name": "当前版本", "data": [86, 92, 88] }],
            "height": 320
          }
        }
      ]
    }
  ]
}
```
