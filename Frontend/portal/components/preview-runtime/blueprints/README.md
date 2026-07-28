# Auto Preview Blueprint Parts Library

This directory contains **Blueprint Parts** for the GamjaBox Auto Preview runtime — category-specific
layouts/dashboards/collections/details/modals/workflows that the deterministic selection engine mounts
in place of the base components. 포털 라이브 프리뷰와 배포 앱 양쪽에서 같은 파츠가 렌더된다.

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

## Integration (현재 상태 — 선택 엔진으로 배선됨)

이 파츠들은 이제 **결정론 선택 엔진**을 통해 파이프라인에 연결돼 있다(Phase A/B 완료):

- 백엔드 `ResourceCategoryClassifier`가 리소스명 → `BlueprintCategory`로 분류하고,
  `BlueprintPartSelector`가 `BlueprintCompiler.compile` 직후 Block의 componentId를 카테고리에 맞는
  파츠로 치환한다(근거 없으면 기본 컴포넌트 유지).
- 포털 라이브 프리뷰: `PreviewController.blocks`에서 선택기 적용 → `adapters/`가 fetch+매핑해 렌더.
- 배포 앱: `PreviewComposeArtifactBuilder.buildWithRealComponents`가 이 preview-runtime **실물**을
  그대로 번들(Tailwind v4). 배포와 포털이 같은 컴포넌트를 쓴다.

### 새 파츠 배선 = 딱 2줄
1. **프론트** `adapters/registry.tsx`의 `BLUEPRINT_PARTS`에 `"my-part": { kind, render }` 한 줄.
   (여기서 `ComponentId` union·`isXPart`·finder·PreviewPageRenderer dispatch가 자동 파생)
2. **백엔드** `BlueprintPartRegistry.java`의 `ALL`에 `new BlueprintPart(...)` 한 줄.
   (componentId 문자열이 두 곳에서 동일해야 함)

자세한 절차: `files/auto-preview-design/Auto_Preview_Blueprint_Authoring_Guide.md`의 레시피 A0.
`styles/blueprint-tokens.css`의 추가 토큰이 필요하면 그때 전역 CSS에서 import한다(기본 상태 토큰은
이미 index.css/globals.css의 `--preview-status-*`로 제공됨).

## Guardrails preserved

- Parts use existing Portal UI primitives and Tailwind tokens.
- Status presentation uses the current Preview status-token system.
- No arbitrary HTML, JavaScript evaluation, external package, or direct network request is introduced.
- Flow presets use the existing restricted `PreviewFlowBlueprint` and `PreviewApiBinding` structures.
- Destructive presets require an explicit confirmation context.
- Polling presets use bounded intervals and timeouts.
- Components are adapter-friendly: data and actions arrive through props instead of hidden API calls.

## Status

- **Wired (rendering now)**: 선택 엔진에 등록된 대표 세트 — `entity-directory`, `kanban-collection`,
  `commerce-product-grid`, `infrastructure-resource-detail`, `operations-health-dashboard`.
  포털 프리뷰와 배포 앱 양쪽에서 카테고리 매칭 시 자동 렌더된다.
- **Library (등록 대기)**: 나머지 파츠(모달/워크플로우/추가 컬렉션·상세·대시보드)는 소스로 존재하며,
  위 "2줄 배선"으로 등록하면 바로 살아난다(Phase D에서 category별 확장 예정).
