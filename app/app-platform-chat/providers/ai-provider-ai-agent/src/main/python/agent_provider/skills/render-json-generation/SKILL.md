---
name: render-json-generation
description: Generate a validated Render JSON page from a frozen component test case and a successful controlled data preview. Use when an Agent needs a data list, form, line chart, combo chart, or radar chart; needs the exact component contract or test fixture; or must produce Render JSON while minimizing model-authored JSON.
---

# Render JSON Generation

Build Render JSON by materializing a frozen component test case. Do not author a RenderDocument from scratch.

## Load the minimum evidence

1. Require an accepted `DataContract` and successful `data_preview_query_tool` result before selecting a case.
2. Read `assets/component-test-cases/<case>.json` for the selected component.
3. Read only the matching `references/components/<component>.md` when its capability or field-count rule is needed.

The current frozen catalog contains five generation cases:

- `list-table` for `zg-list-main-layout`
- `form-edit` for `form-main-layout`
- `line-chart` for `line-chart-renderer`
- `combo-chart` for `combo-chart-renderer`
- `radar-chart` for `radar-chart-renderer`

## Use a fixture, not free-form JSON

1. Select exactly one case whose documented purpose matches the request. For a request to show database rows, always select `list-table`.
2. Copy only the preview-approved datasource facts into the tool input: `model`, `fields`, `filters`, `sorts`, `page`, `page_size`, and `key`.
3. Do not alter component, version, page ID, node IDs, layout, props, event definitions, actions, bindings, or any non-datasource part of a test case.
4. Do not write a full RenderDocument, JSON string, Markdown code block, SQL, URL, credential, handler, or expression.
5. Call `render_json_validate_tool` with `component_test_case` and the datasource object. The tool materializes the test case, derives field columns/bindings deterministically, and validates the resulting RenderDocument.

For a list case, the tool creates one visible table column for every approved datasource field. Keep the incoming field order from the preview. This is intentional: it removes column and layout invention from the model.

Use empty `filters` and `sorts` unless the accepted contract explicitly supplies them. `in` and `not_in` use an array in `filters[].value`; `is_null` and `is_not_null` use `value: null`. Filters are materialized as an implicit-AND `filter_dict`; never construct `filterExpr`.

## Repair only datasource facts

If the tool returns a validation failure, correct only values that came from the preview proof. Reload the selected test case if unsure. Retry at most three times. Never bypass a validator rule or replace a frozen test case with hand-written JSON.

## Return evidence

Return the ApplicationBrief, DataContract, successful data preview, ApplicationPlan, and the tool-derived `render-document` plus `validation-report`. Do not reproduce the RenderDocument in the final artifact envelope; the runtime preserves the exact validated document from the tool result.
