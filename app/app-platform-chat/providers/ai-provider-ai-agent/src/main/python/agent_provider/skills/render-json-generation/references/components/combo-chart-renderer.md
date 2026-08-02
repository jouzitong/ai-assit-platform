# `combo-chart-renderer`

## Purpose

Compare one or more bar measures with optional line measures on aligned categories and dual axes.

## Frozen contract

- Version: `1.0.0`
- Required props: `categories`, `barSeries`
- Optional props: `lineSeries`, `option`, `height`, `colors`, `legend`, `loading`, `leftUnit`, `rightUnit`
- Events: none

The fixture maps the first datasource field to the category, the second to a bar series, and the remaining fields to line series on the right axis. The materializer rewrites those binding slots.

## Datasource mapping

Provide at least three preview-approved fields in category/bar/line order. Use the preview's semantic model and bounded list projection; never describe a second query in the component props.

## Invariants

- All arrays align to the same category order.
- Keep units explicit only when the previewed measures have known units.
- `option` remains serializable and cannot contain callbacks or executable strings.
