# `zg-list-main-layout`

## Purpose

Render a pageable business table from a declarative list schema. Use this case for requests such as “把这些记录用数据列表展示”。The renderer emits semantic events; it does not perform business writes.

## Frozen contract

- Version: `1.0.0`
- Required prop: `schema`
- Optional runtime props: `data`, `state`, `records`, `treeData`, `loading`, `total`
- Events: `action`, `itemAction`, `queryChange`, `reload`
- Schema component: `zg-common-list` for a normal table or `zg-common-tree-list` for a tree table

The generation fixture always uses a normal table, no filters, no row actions, and bounded pagination. The materializer fills `schema.fields` from the approved `datasource.fields` in the same order. Each generated field is `{ key, name, label, field: [key] }`.

## Datasource mapping

Use `db-query-list` at the node level. `model`, `filter_dict`, `page`, `page_size`, and `ext.fields` must come from the successful preview. The materializer uses `filter_dict` for the bounded implicit-AND filter set and does not invent a `filterExpr`. `ext.relations` is deliberately not part of this first deterministic fixture because a single preview proof cannot establish a second model.

## Invariants

- Keep every field a stable semantic identifier.
- Do not hand-edit `props.schema.fields`; change the datasource field list and let the tool derive columns.
- Do not add SQL, URLs, headers, credentials, executable strings, or arbitrary actions.
- Keep page and page size within the datasource limits.
