# Query Types

Use only fields, relations, filters, time ranges, and aggregations returned by the accepted `DataContract` and successful data preview. Copy the concrete starting points from `assets/query-patterns.json`.

| Query type | Datasource type | Required declaration | Result shape |
| --- | --- | --- | --- |
| `list` | `db-query-list` | model, fixed/dynamic filters, page, page size, `ext.fields`, previewed relations, sort | pageable records |
| `count` | `semantic-query` | concrete fields, exactly one `count` measure, proof references | one metric row |
| `aggregate` | `semantic-query` | concrete fields, one or more measures, optional dimensions/filters/time range/sorts, proof references | grouped or summary metric rows |

## Count

Use `queryType: "count"` instead of manufacturing a Count/KPI renderer. It must declare exactly one measure with `aggregation: "count"`, and it cannot declare dimensions. Bind the returned alias to a component only after the live catalog proves that component accepts the target prop.

## Aggregate

Use `queryType: "aggregate"` for `sum`, `avg`, `min`, `max`, or `count` measures. Put all measure and dimension fields in `fields`; use `dimensions` only for grouping. A time range must declare its concrete field and either a previewed preset or both explicit bounds.

## Filters and sorting

- Scalar operators (`eq`, `ne`, `gt`, `gte`, `lt`, `lte`, `like`, `starts_with`, `ends_with`) require `value`.
- `in` and `not_in` require a non-empty `values` array.
- `is_null` and `is_not_null` have neither `value` nor `values`.
- Use `sorts[].direction` as `ASC` or `DESC`; use `limit` only for grouped/aggregate result rows.

Do not combine the legacy `filter_dict`/`filterExpr` list syntax with semantic `filters`. Do not emit SQL, endpoints, credentials, or guessed fields.
