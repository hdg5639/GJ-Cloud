# Auto Preview Blueprint Parts Library

This directory contains **frontend-only Blueprint Parts** for the GamjaBox Auto Preview runtime.
No backend contracts, compiler changes, resolver changes, or deployment-template changes are included in this package.

## Package scope

- 8 reusable layout parts
- 6 dashboard parts
- 8 collection parts
- 6 detail parts
- 11 specialized modal parts plus a shared modal frame
- 6 workflow wizard parts
- 14 FlowBlueprint preset factories
- component, layout, and flow manifests
- optional Blueprint-specific CSS tokens

## Directory

```text
Frontend/portal/components/preview-runtime/blueprints/
├─ core/
├─ layouts/
├─ dashboards/
├─ collections/
├─ details/
├─ modals/
├─ workflows/
├─ flows/
├─ manifests/
├─ styles/
└─ index.ts
```

## Categories covered

- Administration and governance
- Analytics and executive dashboards
- Commerce catalog, order, and revenue
- Content authoring and publishing
- CRM, directory, customer, and onboarding
- Infrastructure provisioning and resource details
- Observability, alerts, incidents, and operations
- Project delivery and Kanban workflows
- Settings and permission management
- Approval, deployment, import, transfer, and status workflows

## Integration

1. Copy this directory into the existing project at the same path.
2. Import candidate parts from `preview-runtime/blueprints`.
3. Register selected IDs in the existing frontend `ComponentId` union and runtime renderer.
4. Add matching backend `ComponentContract` entries and deployed static runtime implementations separately.
5. Keep Portal and deployed runtime behavior equivalent.
6. Import `styles/blueprint-tokens.css` from the Portal global stylesheet only when the extra tokens are needed.

## Guardrails preserved

- Parts use existing Portal UI primitives and Tailwind tokens.
- Status presentation uses the current Preview status-token system.
- No arbitrary HTML, JavaScript evaluation, external package, or direct network request is introduced.
- Flow presets use the existing restricted `PreviewFlowBlueprint` and `PreviewApiBinding` structures.
- Destructive presets require an explicit confirmation context.
- Polling presets use bounded intervals and timeouts.
- Components are adapter-friendly: data and actions arrive through props instead of hidden API calls.

## Important

These files are intentionally **not wired into the current resolver/compiler**. They are a Blueprint Parts source library. Select and integrate only the parts whose backend contract, slot, binding, and static deployment implementation are added together.
