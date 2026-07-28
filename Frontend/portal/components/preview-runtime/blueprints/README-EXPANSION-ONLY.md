# GamjaBox Auto Preview — Blueprint Expansion Pack Only

This ZIP contains only the second Blueprint expansion batch.

It intentionally excludes all first-pack component implementation files.

## Installation

Extract this archive over a project where the first Blueprint Parts pack is already installed.

Target directory:

```text
GJ-Cloud-develop/Frontend/portal/components/preview-runtime/blueprints/
```

## Contents

- New UI Parts: 236
- Product Recipes: 20
- Additional Flow Presets: 32
- New implementation files: 248
- Integration/barrel files updated: 11
- First-pack implementation files duplicated: 0

The integration files update existing barrel exports and catalog types so the new Parts can be imported through the existing Blueprint library.

## Important

This file describes the historical second-pack payload. In the current repository all 236 expansion
Parts are included in the canonical `manifests/component-manifest.json`; code generation and the shared
Portal/deployment runtime wire them without a second hand-maintained registry.
