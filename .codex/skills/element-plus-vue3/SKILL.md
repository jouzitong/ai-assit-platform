---
name: element-plus-vue3
description: Develop, review, and troubleshoot Element Plus UI in this repository's active Vue 3 frontend, ai-conversation-ui. Use for el-* components, forms, tables, dialogs, navigation, feedback, icons, theme customization, internationalization, component APIs, and Element Plus integration while preserving the project's routing, page, component, request, theme, and build conventions.
---

# Element Plus Vue 3 for ai-assit-platform

Use the bundled Element Plus references as supporting material. Treat the current repository implementation and development specifications as the source of truth.

## Required workflow

1. Locate and read the repository `AGENTS.md` files that govern the target path before analysis or edits.
2. Target `ai-conversation-ui/`, the active Vue frontend. Do not move work to historical frontend directories unless the user explicitly asks.
3. Read `docs/dev-spec/README.md`, then load only the relevant frontend specifications:
   - routes: `docs/dev-spec/detail/frontend/router.md`
   - styles: `docs/dev-spec/detail/frontend/style.md`
   - themes and Element Plus variables: `docs/dev-spec/detail/frontend/theme.md`
   - reusable components: `docs/dev-spec/detail/frontend/component.md`
   - Render JSON and application renderers: `docs/dev-spec/detail/frontend/application.md`
4. Inspect the target page, nearby components, and `ai-conversation-ui/package.json` before choosing an API or pattern. Installed dependency versions override examples in this skill.
5. Reuse existing project components and interaction patterns before adding new wrappers.
6. After edits, run `npm run build` from `ai-conversation-ui/`. Do not perform browser interaction or visual acceptance unless the user explicitly requests it.

## Current integration baseline

- Vue, Element Plus, and `@element-plus/icons-vue` versions come from `ai-conversation-ui/package.json`; verify them instead of relying on a copied version number.
- Element Plus is registered globally in `ai-conversation-ui/src/main.ts`, and its base CSS is imported there. Do not switch the project to on-demand registration unless the task explicitly requires that architectural change.
- Use Vue 3 Composition API with `<script setup lang="ts">`, matching surrounding code.
- Import icons from `@element-plus/icons-vue`; use Element Plus icons instead of emoji or improvised glyphs for controls.
- Import programmatic APIs such as `ElMessage` and `ElMessageBox` from `element-plus` when needed.
- Keep remote calls in the page module's `api/` or `service/` layer and use the shared request path rooted at `ai-conversation-ui/src/api/request.ts`. Do not call `fetch` directly from an Element Plus view or reusable component.
- Reuse shared components such as the project pagination component when an established equivalent exists.

## Styling and theme rules

- Use the existing theme tokens in `ai-conversation-ui/src/styles/variables.scss`.
- Element Plus theme values are mapped through the project's `--el-*` variables. Extend that mapping centrally when a semantic global value is missing.
- Do not scatter colors, spacing, radii, shadows, or control sizes through page components when a project token already exists.
- Keep page-specific layout styles scoped to the page. Put reusable, non-business layout primitives under the project's shared component layer.
- Preserve light and dark theme behavior. Check focus, disabled, loading, empty, error, dialog cleanup, and responsive states relevant to the changed UI.
- Use `:deep(...)` only when a scoped component must target Element Plus internals; keep overrides narrow and anchored to the owning component.

## Component behavior rules

- Forms: define stable models, validation, loading state, reset behavior, and dialog/drawer cleanup. Prevent duplicate submission while saving.
- Tables: provide loading, empty, error, and pagination behavior; keep row action widths and fixed columns deliberate.
- Dialogs and drawers: use controlled visibility, meaningful titles, explicit cancel/confirm actions, and cleanup through close lifecycle hooks when state must not persist.
- Feedback: show actionable success and error messages; distinguish cancellation from real errors for confirmation dialogs.
- Components: keep props and emitted events explicit. Views render and emit; service or page orchestration owns requests and business workflows.
- Accessibility: retain labels, keyboard focus, semantic status cues, and sufficient contrast. Color must not be the only state indicator.

## Bundled reference routing

Load only the reference needed for the task.

### Guides

- installation: `examples/guide/installation.md`
- quick start: `examples/guide/quick-start.md`
- design: `examples/guide/design.md`
- internationalization: `examples/guide/i18n.md`
- theme customization: `examples/guide/theme.md`
- global configuration: `examples/guide/global-config.md`

### Component examples included in this package

- overview: `examples/components/overview.md`
- button: `examples/components/button.md`
- input: `examples/components/input.md`
- form: `examples/components/form.md`
- table: `examples/components/table.md`
- dialog: `examples/components/dialog.md`
- message: `examples/components/message.md`
- date picker: `examples/components/date-picker.md`
- select: `examples/components/select.md`

For components not listed above, inspect the installed Element Plus types and current official documentation. Do not assume that an unlisted local reference file exists.

### API references

- component APIs: `api/component-api.md`
- props and events: `api/props-and-events.md`
- global configuration: `api/global-config.md`

### Templates

- installation: `templates/installation.md`
- component usage: `templates/component-usage.md`
- project setup: `templates/project-setup.md`

Adapt templates to the repository. Never replace existing project setup or conventions merely because a generic template differs.

## Source and license

This project-local skill is adapted from `full-stack-skills/vue-ui-skills`, skill `element-plus-vue3`. The bundled upstream material is licensed under Apache License 2.0; retain `LICENSE.txt` when redistributing it.
