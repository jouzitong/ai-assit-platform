# `line-chart-renderer`

## Purpose

Show a category/time trend with one or more aligned numeric series.

## Frozen contract

- Version: `1.0.0`
- Required props: `categories`, `series`
- Optional props: `option`, `height`, `unit`, `colors`, `smooth`, `area`, `showSymbol`, `legend`, `loading`
- Events: none

The fixture uses the first datasource field as `bindings.category` and every remaining field as a series binding. The runtime derives categories and numeric series from returned records, so the model does not write arrays of sample values.

## Datasource mapping

Provide at least two preview-approved fields: one category and one numeric measure. Keep their order. The node uses `db-query-list`; the materializer writes the field projection and rewrites binding slots.

## Invariants

- Every series must align with the category length.
- `option` is plain JSON only; no formatter functions, callbacks, or runtime instances.
- Do not use this fixture for a count-only result; use a component whose contract accepts that metric.
