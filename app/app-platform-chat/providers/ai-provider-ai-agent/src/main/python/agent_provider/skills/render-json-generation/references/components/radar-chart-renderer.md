# `radar-chart-renderer`

## Purpose

Show a normalized multi-dimensional profile for one or more records.

## Frozen contract

- Version: `1.0.0`
- Required props: `indicators`, `series`
- Optional props: `option`, `height`, `colors`, `legend`, `loading`
- Events: none

The fixture turns every datasource field into an indicator binding with a deterministic maximum of `100`. The runtime maps each returned record to one series; no model-authored numeric arrays are needed.

## Datasource mapping

Provide three to eight preview-approved numeric fields. Their order becomes the indicator order. If the source values are not comparable or normalized, stop and clarify instead of silently scaling them.

## Invariants

- Every series has exactly the same indicator order and length.
- Keep `option` plain JSON and avoid runtime callbacks.
- Do not use a radar fixture for an unbounded or high-cardinality record set.
