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

- **Wired (rendering now)** — Phase D에서 collection/detail/dashboard kind를 category별로 확장 배선함.
  카테고리 매칭 시 포털 프리뷰·배포 앱 양쪽에서 자동 렌더된다:
  - 컬렉션: `entity-directory`(CRM), `kanban-collection`(PROJECT), `commerce-product-grid`(COMMERCE),
    `alert-inbox`(OBSERVABILITY), `audit-log-table`(ADMIN), `compact-metric-table`(ANALYTICS),
    `media-gallery-collection`(CONTENT)
  - 상세: `infrastructure-resource-detail`(INFRASTRUCTURE), `commerce-order-detail`(COMMERCE),
    `content-article-detail`(CONTENT), `customer-profile-detail`(CRM), `incident-detail`(OBSERVABILITY),
    `settings-detail`(SETTINGS)
  - 대시보드: `operations-health-dashboard`(OBSERVABILITY), `admin-governance-dashboard`(ADMIN),
    `commerce-revenue-dashboard`(COMMERCE), `content-performance-dashboard`(CONTENT),
    `executive-kpi-dashboard`(ANALYTICS), `project-delivery-dashboard`(PROJECT)
- **Expansion Pack (config 팩토리 기반, 배선됨)** — `core/megaFactory.tsx`의 `create*Part(config)`로
  만들어진 확장 팩. collection/detail/dashboard **86종**을 `adapters/megaParts.tsx`에서 배선했다(균일
  시그니처라 개별 매퍼 없이 `records=rows` 한 줄). 신규 도메인 카테고리 20종(SECURITY/FINANCE/HR/IOT/
  LEGAL/…)을 백엔드 `BlueprintCategory` enum + `ResourceCategoryClassifier` 힌트 + 프론트 `catalog.ts`에
  추가해 자동선택이 걸린다. 확장 팩 파츠는 `preferredPurposes=ADMIN/PRODUCT_LIKE`라 API_TEST에선 기본
  컴포넌트가 유지된다. (kind, category)당 첫 파츠만 자동선택되고, 같은 카테고리의 나머지는 마법사
  드롭다운(오버라이드)으로 도달한다.
- **Library (등록 대기)**: 모달/워크플로우 위저드 + 확장 팩의 layouts/modals/workflows/forms/actions/
  navigation/feedback/themes **150종**은 렌더 kind가 달라(드롭인 대체가 아님) 이 selector 배선 대상이
  아니다 — 레이아웃 선택 엔진·테마 시스템 등 별도 런타임이 생겨야 소비된다(Combined Report §29 로드맵).
  소스로 존재하며 컴파일된다. `timeline-collection` 등 카테고리 귀속이 모호한 컬렉션도 자동선택 미등록
  (오버라이드로는 지정 가능).
