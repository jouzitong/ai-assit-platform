# Public renderer components

## Contents

- Source and version policy
- `zg-list-main-layout`
- `form-main-layout`
- `line-chart-renderer`
- `combo-chart-renderer`
- `radar-chart-renderer`

## Source and version policy

This reference mirrors the frontend Renderer Catalog and Component Manifest at authoring time. The snapshot version is `1.0.0`. Query the live component catalog before final output; its key, version, required props, and events override this reference.

## `zg-list-main-layout`

Aliases: `list-main-layout`, `zg-common-list`, `zg-common-tree-list`, `common-list`, `common-tree-list`.

Use for management lists, optional tree grouping, tabs, filters, summary cards, row actions, and pagination. The renderer emits semantic events and does not execute requests or business actions itself.

Important props:

| Prop | Required | Purpose |
| --- | --- | --- |
| `schema` | yes | List title, fields, filters, actions, tabs, tree, summary, and pagination. |
| `data` | no | Preferred `{ records, treeData?, total? }` input. |
| `state` | no | Preferred `{ loading?, empty?, error? }` state. |
| `records`, `treeData`, `loading`, `total` | no | Legacy compatibility inputs. Prefer `data` and `state`. |

Events: `action`, `itemAction`, `queryChange`, `reload`.

Schema notes:

- Require `schema.id`; set `schema.version` to the component version.
- Use `schema.component: "zg-common-list"` for a normal list or `"zg-common-tree-list"` when tree navigation is required.
- Define filters as `{ key, name|label, component, options }`. Put `placeholder`, `list`, `multiple`, and query behavior inside `options`.
- Define fields as `{ key, name, label, field?, options? }`; `field` is a nested path array.
- Define renderer buttons as `{ key, name, action, options? }`.
- Configure pagination in `schema.list_config.pagination`.
- Put a concrete `db-query-list` in node `datasource`; the runtime merges it into schema. Examples must include model, fields, filters, relations, and sort rules from preview.

Reference node:

```json
{
  "id": "task-list",
  "component": "zg-list-main-layout",
  "componentVersion": "1.0.0",
  "props": {
    "schema": {
      "id": "task-list-schema",
      "version": "1.0.0",
      "title": "任务中心",
      "component": "zg-common-list",
      "filters": [
        {
            "key": "title",
            "label": "任务标题",
            "component": "zg-input",
            "options": {
              "placeholder": "输入名称后回车搜索",
              "query": { "field": "title", "op": "like", "submitOnEnter": true }
            }
        }
      ],
      "fields": [
        { "key": "id", "name": "id", "label": "编号", "field": ["id"] },
        { "key": "title", "name": "title", "label": "任务标题", "field": ["title"] },
        { "key": "owner", "name": "owner", "label": "负责人", "field": ["owner"] }
      ],
      "actions": [
        { "key": "refresh", "name": "刷新", "action": "RELOAD", "options": { "type": "primary", "icon": "refresh" } }
      ],
      "list_config": {
        "itemType": "table",
        "pagination": { "enabled": true, "pageSize": 10, "pageSizeOptions": [10, 20, 50] }
      }
    }
  },
  "datasource": {
    "key": "knowledge-document-query",
    "type": "db-query-list",
    "model": "ai_kb_document",
    "filter_dict": { "status": 1, "is_delete": 0 },
    "filterExpr": "status and is_delete",
    "page": 1,
    "page_size": 10,
    "ext": {
      "fields": ["id", "title", "owner", "deadline", "priority", "tag.title"],
      "relations": [
        {
          "key": "tag",
          "model": "ai_kb_document_tag",
          "type": "left",
          "on": { "id": "document_id" },
          "filter": { "status": 1, "is_delete": 0 }
        }
      ],
      "sorts": [{ "field": "id", "order": "desc" }]
    }
  },
  "layout": { "minHeight": "520px" }
}
```

## `form-main-layout`

Aliases: `zg-common-form`, `zg-common-info`, `common-form`, `common-info`.

Use for view, add, and edit forms with dynamic fields, groups, grid spans, defaults, and submit/reset actions.

Important props:

| Prop | Required | Purpose |
| --- | --- | --- |
| `schema` | yes | Fields, groups, actions, form configuration, and initial data. |
| `modelValue` | no | Controlled form values. |
| `readonly` | no | Force all fields to read-only. |
| `formMode` | no | `view`, `edit`, or `add`. |
| `submitting` | no | Disable actions and show submit loading state. |

Events: `action`, `change`, `update:modelValue`, `submit`, `reset`.

Schema notes:

- Require `schema.id`; set `schema.version` to the component version.
- Field types include `text`, `textarea`, `select`, `date`, `daterange`, `time`, `checkbox`, `switch`, `code`, and `display`.
- Put hidden state on `field.hide`. Hidden fields retain their values.
- Configure `field.options.labelPosition` with `left`, `right`, `top`, or `inline`.
- Configure `field.options.span` from 1 through 12.
- Organize fields with `schema.groups[].fields`.
- Put defaults in `schema.form_config.defaultValues` and a stable submit executor in `schema.form_config.submit.executor` only when the runtime registers it.

Reference node:

```json
{
  "id": "asset-form",
  "component": "form-main-layout",
  "componentVersion": "1.0.0",
  "props": {
    "schema": {
      "id": "asset-form-schema",
      "version": "1.0.0",
      "title": "组件资产信息",
      "component": "zg-common-form",
      "fields": [
        {
          "key": "name",
          "name": "name",
          "label": "资产名称",
          "component": "zg-input",
          "type": "text",
          "options": { "required": true, "span": 6, "labelPosition": "left" }
        },
        {
          "key": "enabled",
          "name": "enabled",
          "label": "启用状态",
          "type": "switch",
          "options": { "span": 6, "labelPosition": "right" }
        }
      ],
      "groups": [
        { "key": "base", "title": "基础信息", "fields": ["name", "enabled"], "columns": 2 }
      ],
      "actions": [
        { "key": "save", "name": "保存", "action": "SAVE", "options": { "type": "primary" } },
        { "key": "reset", "name": "重置", "action": "RESET" }
      ],
      "form_config": { "columns": 2, "actionsAlign": "right", "defaultValues": { "enabled": true } }
    },
    "modelValue": { "name": "通用列表渲染器", "enabled": true },
    "readonly": false,
    "formMode": "edit"
  },
  "layout": { "minHeight": "480px" }
}
```

## `line-chart-renderer`

Alias: `zg-line-chart-renderer`.

Use for time trends and multiple aligned series. Require `categories` and `series`. Optional props are `option`, `height`, `unit`, `colors`, `smooth`, `area`, `showSymbol`, `legend`, and `loading`. This component publishes no events.

Ensure every `series.data` aligns with `categories`. Keep `option` fully serializable.

```json
{
  "id": "monthly-trend",
  "component": "line-chart-renderer",
  "componentVersion": "1.0.0",
  "props": {
    "categories": ["1月", "2月", "3月"],
    "series": [
      { "name": "成交额", "data": [128, 176, 204], "smooth": true },
      { "name": "目标值", "data": [140, 160, 190], "showSymbol": true }
    ],
    "height": 320,
    "unit": " 万元",
    "smooth": true,
    "area": true,
    "showSymbol": false,
    "legend": true,
    "loading": false,
    "option": {}
  },
  "layout": { "minHeight": "360px" }
}
```

## `combo-chart-renderer`

Alias: `zg-combo-chart-renderer`.

Use for bar values and line rates on aligned categories and two Y axes. Require `categories` and `barSeries`. Optional props are `lineSeries`, `option`, `height`, `colors`, `legend`, `loading`, `leftUnit`, and `rightUnit`. This component publishes no events.

```json
{
  "id": "traffic-conversion",
  "component": "combo-chart-renderer",
  "componentVersion": "1.0.0",
  "props": {
    "categories": ["1月", "2月", "3月"],
    "barSeries": [
      { "name": "访问量", "data": [620, 760, 880], "barMaxWidth": 26 },
      { "name": "下单量", "data": [132, 168, 214], "barMaxWidth": 26 }
    ],
    "lineSeries": [
      { "name": "转化率", "data": [21.3, 22.1, 24.3], "yAxisIndex": 1, "smooth": true }
    ],
    "height": 340,
    "legend": true,
    "loading": false,
    "leftUnit": " 次",
    "rightUnit": "%",
    "option": {}
  },
  "layout": { "minHeight": "380px" }
}
```

## `radar-chart-renderer`

Alias: `zg-radar-chart-renderer`.

Use for normalized multi-dimensional profiles. Require `indicators` and `series`. Optional props are `option`, `height`, `colors`, `legend`, and `loading`. This component publishes no events.

Keep 3 to 8 indicators. Ensure every series has the same order and length as `indicators`.

```json
{
  "id": "quality-profile",
  "component": "radar-chart-renderer",
  "componentVersion": "1.0.0",
  "props": {
    "indicators": [
      { "name": "稳定性", "max": 100 },
      { "name": "易用性", "max": 100 },
      { "name": "性能", "max": 100 }
    ],
    "series": [
      { "name": "当前版本", "data": [86, 78, 92], "opacity": 0.18 },
      { "name": "目标版本", "data": [92, 90, 95], "opacity": 0.08 }
    ],
    "height": 360,
    "legend": true,
    "loading": false,
    "option": {}
  },
  "layout": { "minHeight": "400px" }
}
```
