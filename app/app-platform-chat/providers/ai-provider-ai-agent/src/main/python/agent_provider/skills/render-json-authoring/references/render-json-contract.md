# Render JSON contract

## Contents

- Document shape
- Node shape
- Datasources and bindings
- Events and actions
- Authoring rules
- Validation checklist

## Document shape

Author the Python agent's validator-facing document with only these top-level fields:

| Field | Required | Meaning |
| --- | --- | --- |
| `protocol` | yes | Always `render-json`. |
| `protocolVersion` | yes | Use a version supported by the validator; current bundled schema accepts `1.0` and `1.0.0`. |
| `pageId` | yes | Stable identifier matching `^[A-Za-z][A-Za-z0-9_.:-]{0,127}$`. |
| `revision` | no | Non-empty document revision. |
| `root` | yes | Root Render node. |

The frontend application host can normalize extra page metadata in other flows, but the Python agent validator currently rejects extra top-level fields such as `presentation`, `filters`, and `actions`. Keep this skill's output on the stricter validator contract.

## Node shape

Use only:

```json
{
  "id": "stable-node-id",
  "component": "catalog-component-key",
  "componentVersion": "catalog-version",
  "props": {},
  "layout": {},
  "datasource": {},
  "bindings": {},
  "events": [],
  "actions": [],
  "children": []
}
```

- Require `id`, `component`, and the live `componentVersion` for published components.
- Keep node IDs unique across the complete tree.
- Use `props` only for declared component parameters.
- Use node `layout` for placement: `gridColumn`, `gridRow`, `width`, `height`, and `minHeight` are consumed by the current runtime node.
- Use `children` only with components that the runtime and live catalog both support.
- Do not add Vue component references, callbacks, stores, routers, HTTP clients, or other runtime objects.

## Datasources and bindings

Declare a datasource at node level. The runtime merges it into list/form schema when needed.

### Inline list data

```json
{
  "key": "task-data",
  "type": "direct-json",
  "data": {
    "records": [
      { "id": "TASK-001", "name": "核对 Render JSON" }
    ],
    "total": 1
  }
}
```

### Database list query

```json
{
  "key": "task-query",
  "type": "db-query-list",
  "model": "task",
  "filter_dict": { "status": 1 },
  "filterExpr": "status",
  "page": 1,
  "page_size": 10,
  "ext": {
    "fields": ["id", "name", "owner"],
    "sorts": [{ "field": "id", "order": "desc" }]
  }
}
```

- Use semantic model and field identifiers already accepted by preview.
- Use `direct-json` for prepared JSON and `db-query-list` for the current list resolver.
- Use `semantic-query`, `preview-result`, or `static` only when the current tool contract explicitly authorizes them.
- Never include endpoints, headers, tokens, credentials, physical SQL, or arbitrary URLs.

Bindings map named component targets to semantic result paths:

```json
{
  "categories": { "source": "preview.months", "transform": "identity" },
  "series": { "source": "preview.series", "transform": "identity" }
}
```

Allowed bundled transforms are `identity`, `number`, `percent`, `currency`, `date`, `datetime`, and `category-series`. Use only bindings accepted by the live component and validator contracts.

## Events and actions

Node-level actions use the Python agent contract:

```json
{
  "actions": [
    { "key": "refresh-page", "type": "refresh", "target": "task-list" }
  ],
  "events": [
    { "event": "reload", "actionRef": "refresh-page" }
  ]
}
```

The bundled node action types are `filter.update`, `selection.update`, `navigation.local`, `refresh`, and `dialog.open`. Each event must reference an action declared on the same node, and its event name must be published by that component.

Renderer buttons inside list or form `props.schema` use a different frontend contract:

```json
{
  "key": "save",
  "name": "保存",
  "action": "SAVE",
  "options": { "type": "primary", "icon": "operation" }
}
```

- Put visual configuration only under `options`.
- Allow button types `default`, `primary`, `success`, `warning`, `danger`, and `info`.
- Allow icons `download`, `fullscreen`, `operation`, `print`, and `refresh`.
- Do not migrate renderer buttons into node-level action objects or the reverse.

## Authoring rules

1. Prefer canonical catalog keys; accept aliases only if the live catalog publishes the alias as its exact key.
2. Pin the exact live component version on every node.
3. Keep all values valid JSON. Do not use comments, trailing commas, `NaN`, or duplicate object keys.
4. Avoid dangerous keys such as credentials, headers, URLs, scripts, handlers, expressions, raw SQL, or prototype-related keys.
5. Keep charts' `option` serializable; never place formatter functions in it.
6. Keep list filters' control-specific values inside `filter.options`.
7. Keep filter field mapping and operator inside `filter.options.query`.
8. Keep form hidden state on `field.hide`; use `field.options.hidden` only for legacy input.
9. Use stable IDs and semantic field names instead of array indexes or display labels.

## Validation checklist

- The semantic preview proof succeeded.
- Every component exists in the live catalog.
- Every `componentVersion` equals the published version.
- Every required prop exists and every extra prop is removed.
- Every event is declared by its component and references a local node action.
- Every datasource and binding uses previewed fields.
- Node IDs are unique; depth and node count remain bounded.
- The final `render_json_validate_tool` result reports `valid: true` and includes a catalog revision.
