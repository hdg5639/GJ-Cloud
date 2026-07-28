# Auto Preview Blueprint Parts Library

GamjaBox Auto Preview가 사용하는 Blueprint Parts 라이브러리다. 포털 라이브 프리뷰와 VM에
배포되는 Vite 앱은 모두 이 디렉터리의 같은 React 구현을 사용한다.

## 현재 배선 상태

총 **281개 파츠가 전부 Registry에 연결**되어 있다.

| Kind | Count | Runtime mount |
| --- | ---: | --- |
| ACTION | 16 | `page.actions` |
| COLLECTION | 38 | `page.main` |
| DASHBOARD | 36 | `page.content` |
| DETAIL | 32 | `page.main`, `page.primary` |
| FEEDBACK | 14 | `page.feedback` |
| FORM | 18 | `page.overlay` |
| LAYOUT | 28 | `page.layout` |
| MODAL | 41 | `page.overlay` |
| NAVIGATION | 14 | `page.navigation` |
| THEME | 16 | `page.theme` |
| WORKFLOW | 28 | `page.overlay` |

컬렉션·상세·대시보드뿐 아니라 액션, 폼, 모달, 워크플로우, 레이아웃, 내비게이션, 피드백,
테마까지 마법사에서 선택할 수 있다. 카테고리와 purpose가 명확한 파츠는
`BlueprintPartSelector`가 자동 선택하고, 같은 mount의 다른 파츠는 사용자가 오버라이드할 수 있다.
피드백과 테마는 상태·브랜드 의도를 임의로 추측하지 않도록 수동 선택만 허용한다.

## 단일 정본과 코드 생성

파츠 계약의 유일한 정본은 다음 JSON이다.

```text
manifests/component-manifest.json
```

이 파일에는 `componentId`, 구현 export, kind, category, mount point, surface, purpose, mode,
자동 선택 여부가 들어 있다. 다음 명령이 모든 구현 파일과 export를 검증하고 TypeScript Component
Registry를 생성한다.

```bash
cd Frontend/portal
npm run blueprint:generate
npm run blueprint:check
```

생성 결과:

```text
adapters/generatedPartComponents.ts
```

Ops의 `syncBlueprintManifest`도 같은 JSON을 classpath에 복사한다. 따라서 Java
`BlueprintPartRegistry`, TypeScript `ComponentId`, 파츠 선택기, 사용자 Picker가 별도의 목록을
각자 유지하지 않는다.

## 신규 파츠 등록 절차

1. kind 디렉터리에 React 구현을 추가하고 named export 한다.
2. `component-manifest.json`에 파츠 선언 한 건을 추가한다.
3. `npm run blueprint:generate`를 실행한다.
4. `npm run blueprint:check`, TypeScript 검사, Ops 테스트를 실행한다.

수동 Registry 분기나 Java 문자열 안의 React 재구현은 추가하지 않는다.

## Runtime 경로

```text
component-manifest.json
  ├─ codegen → generatedPartComponents.ts → Portal Registry/Renderer
  └─ Gradle sync → BlueprintPartRegistry → Compiler/Selector

Frontend preview-runtime 실물
  ├─ Portal live preview
  └─ PreviewComposeArtifactBuilder가 배포 아티팩트에 그대로 복사
```

`PreviewBlockResolver`는 데이터 Block 외에 layout/navigation/feedback/theme용 기본 Block을
생성한다. `BlueprintPartSelector`는 kind, category, surface, purpose, mode가 모두 맞는 파츠만
선택한다. 생성·수정·명령·삭제 overlay가 섞이지 않도록 `supportedModes`도 계약으로 검증한다.

## Guardrails

- 파츠는 props로 데이터와 액션을 받고 직접 임의 네트워크 요청을 만들지 않는다.
- 알 수 없는 카테고리는 기본 컴포넌트를 유지한다.
- 호환되지 않는 사용자 오버라이드는 자동 선택 또는 기본 컴포넌트로 폴백한다.
- 파괴적 파츠는 `DELETE` mode에서만 선택된다.
- 배포 앱과 포털은 동일한 Runtime 소스를 사용한다.
