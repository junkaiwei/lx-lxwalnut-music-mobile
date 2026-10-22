# Agent development guide

This is the first file coding agents must read.

## Required reading order

1. `DEVELOPMENT.md`
2. `docs/PROJECT_INDEX.md`
3. `docs/ARCHITECTURE.md`
4. `docs/MEDIA3_DESIGN.md`
5. `docs/MEDIA3_PROGRESS.md`

## Branch rules

- `main-debug`: upstream synchronization only.
- `build/packet-name`: primary development and integration branch.
- Feature branches must start from the latest `build/packet-name`.
- Never develop project features directly on `main-debug`.

## Change control

Before changing dependencies, build tools, Android components, manifests, native modules, package identity, playback lifecycle, QuickJS, networking, cache, Widget or external entry points:

1. Trace the complete dependency chain.
2. Trace manifest ownership and runtime lifecycle.
3. List affected modules and user-visible behavior.
4. Define code-review, build, runtime and regression tests.
5. Record rollback conditions.

If a conflict, unsupported requirement, duplicate owner or incompatible dependency is found, stop immediately. Do not continue by forcing versions, suppressing errors or keeping two implementations active. Report the source, affected components, evidence and viable options.

## Media3 project rules

`docs/MEDIA3_DESIGN.md` is the single normative specification for the Media3 migration.

`docs/MEDIA3_PROGRESS.md` is the single live status record. Every code commit for this migration must update it in the same commit and include:

- phase and status
- changed files and scope
- dependency changes
- affected modules and behavior
- risks and possible impact
- tests executed and results
- blockers or conflicts
- next step

Do not submit migration code without the matching progress update.

## Identity rules

`APP_PACKAGE_NAME` is the single source of truth for Android applicationId, FileProvider authority, Widget action prefix and JavaScript build identity. Do not hardcode package names in Android or JS communication code.

## Validation baseline

Relevant changes must validate the final dependency graph, merged manifest, Debug build, minified Release build, runtime behavior and dynamic package identity. A failed required check blocks the commit or merge unless the failure is explicitly documented as an external blocker and no code is advanced past that gate.
