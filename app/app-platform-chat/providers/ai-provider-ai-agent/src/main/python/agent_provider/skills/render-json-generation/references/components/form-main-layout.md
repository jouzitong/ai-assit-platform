# `form-main-layout`

## Purpose

Render a read-only or editable field form from a declarative schema. Use it for a data record detail or a bounded edit surface, not for arbitrary workflow code.

## Frozen contract

- Version: `1.0.0`
- Required prop: `schema`
- Optional props: `modelValue`, `readonly`, `formMode`, `submitting`
- Events: `action`, `change`, `update:modelValue`, `submit`, `reset`
- Supported modes: `view`, `edit`, `add`

The generation fixture is read-only (`formMode: "view"`) and creates text fields from `datasource.fields`. It does not invent submit executors or write actions.

## Datasource mapping

Use the same preview-approved `db-query-list` shape as the list fixture. The materializer only uses the field identifiers to create stable text fields; it never places a physical table name, SQL, or endpoint into the form.

## Invariants

- Field spans remain within the 12-column grid.
- Hidden fields use the documented `hide` property only.
- `form_config.submit.executor` is omitted unless a registered executor is explicitly part of the run.
- Keep all values JSON-serializable and keep the form read-only unless the user explicitly requests editing.
