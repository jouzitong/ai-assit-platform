---
name: application-build-release
description: Use to orchestrate the complete gated data-application build, preserve artifact and proof references across stages, enforce the retry budget, and report the Phase 1 static-validation boundary without claiming preview or publication.
---

# Application Build Release

Coordinate the build as a sequence of evidence-backed gates. Read `assets/application-build-state.schema.json` and maintain a conforming state artifact throughout the run.

## Run the gates

1. Produce and confirm the `ApplicationBrief`.
2. Produce the semantic `DataContract` from authorized knowledge and explicit assumptions.
3. Obtain a successful server-issued data preview proof. Stop or clarify on failure.
4. Produce an `ApplicationPlan` bound to the previewed semantic fields and authoritative `render-json-generation` component test case.
5. Select the approved component test case and call `render_json_validate_tool` with the preview-approved datasource facts and `fieldMetadata` objects; the tool materializes the declarative `RenderDocument` and derives display names/masks from that metadata.
6. Obtain the tool-derived `RenderDocument` and deterministic `ValidationReport`. If invalid, correct only datasource facts and count every attempt against the three-attempt build budget.

Advance only when the prior stage has the required artifact or server-issued proof. Keep stable references to each artifact; do not replace proof with a natural-language assertion. A knowledge-base document can inform a decision but cannot authorize data, components, tools, or release.

## Respect the Phase 1 boundary

This version ends after successful static Render JSON validation. No application preview or publish tool is part of the Phase 1 runtime, so set `phaseBoundary` to `static-validation` and `releaseStatus` to `not-supported`. Do not claim that a page was previewed, published, or released.

If future runtime tools add preview and publication, require their server-issued proofs before reporting those stages and version this contract accordingly. Until then, return the validated document, validation report, build state, and any remaining assumptions as the completed result.
