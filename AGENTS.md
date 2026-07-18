# Agent development guide

This file is the first entry point for coding agents working in this repository.
Read `DEVELOPMENT.md` and `docs/PROJECT_INDEX.md` before making non-trivial changes.

## Branch policy

- `main-debug` is the upstream synchronization branch only.
- `build/packet-name` is the long-lived development and integration branch.
- Never develop features directly on `main-debug`.
- Before feature work, sync upstream into `main-debug`, merge `main-debug` into `build/packet-name`, then create the feature branch from the latest `build/packet-name`.
- Merge completed features back into `build/packet-name`.

## Project shape

- React Native 0.73 application using React Native Navigation.
- JavaScript/TypeScript entry: `index.js` -> `src/app