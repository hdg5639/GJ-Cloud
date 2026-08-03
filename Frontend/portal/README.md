# GamjaBox Portal

GamjaBox 사용자 포털과 ControlBox 관리자 콘솔을 함께 제공하는 Next.js App Router 애플리케이션이다. 하나의 빌드 결과를 사용하지만 도메인과 라우팅, 권한, 레이아웃을 분리한다.

> 전체 시스템 구성은 [프로젝트 루트 README](../../README.md)를 참고한다.

## 제공 화면

### 사용자 포털

- 로그인, 회원가입, 이메일 인증, 비밀번호 재설정, 온보딩
- VM 목록·생성·상세·전원·플랜·SSH Access·포트
- 실시간 메트릭, 웹 SSH 콘솔, 파일 브라우저
- Docker 관리, DB 백업, 배포 대상·이력·GitHub 자동 배포
- OpenAPI 기반 Auto Preview 분석·시나리오 실행·VM 배포
- 조직·멤버·VM 공유와 협업 메모·공지·요청
- SSH 키, 프로필, 보안 설정과 사용자 설명서

### ControlBox

- 사용자와 VM 관리
- 플랜 업그레이드 요청 승인
- Docs 문서 작성·미리보기·발행·이미지 관리

`ADMIN_DOMAIN`과 요청 Host가 일치하면 middleware가 일반 경로를 `/admin` 영역으로 rewrite한다. 일반 사용자 도메인에서 `/admin` 직접 접근은 차단하며, 환경변수가 없으면 관리자 라우팅을 fail-safe로 비활성화한다. 최종 권한은 각 백엔드의 `/admin/**` API가 다시 검증한다.

## 기술 구성

- Next.js 16 App Router, React 19, TypeScript
- Tailwind CSS 4
- Recharts 메트릭 시각화
- xterm.js 웹 SSH 터미널
- react-markdown + remark-gfm Docs 렌더링
- SSE 상태·메트릭·배포 로그, WebSocket 터미널
- `output: standalone` 프로덕션 빌드

## 라우트 구조

```text
app/
├── (auth)/                 로그인·가입·인증·온보딩
├── (dashboard)/
│   ├── instances/          VM 생성·상세·운영 기능
│   ├── organizations/      조직과 협업
│   ├── docs/               사용자 설명서
│   ├── settings/           프로필·보안·회원 탈퇴
│   └── ssh-keys/           SSH 키
├── admin/                  ControlBox 전용 화면
└── preview-demo/           Preview Runtime 개발 확인
```

공통 API 계약과 타입은 `lib/api-client.ts`, `lib/types.ts`에 둔다. 재사용 UI는 `components/ui`, Auto Preview 실행 UI는 `components/preview-runtime`에 둔다.

## 인증과 API 호출

```text
로그인
  → Access Token을 React 메모리에 보관
  → Refresh Token은 Auth의 httpOnly 쿠키
  → API 호출 직전 대상 서비스 audience로 Token Exchange
  → 만료 전 자동 refresh
```

- Access Token과 서비스 토큰을 localStorage에 저장하지 않는다.
- 네트워크 오류·429·5xx에서는 세션을 유지하고 갱신을 재시도한다.
- 400/401/403처럼 실제 세션 무효가 확인된 경우에만 로그아웃한다.
- Web Locks로 여러 탭의 Refresh Token Rotation을 직렬화한다.
- BroadcastChannel로 신규 토큰과 로그아웃 상태를 탭 간 동기화한다.
- 절전 복귀, 탭 활성화, 온라인 복귀 시 만료 임박 토큰을 다시 확인한다.

## 환경변수

클라이언트에서 필요한 값만 `NEXT_PUBLIC_`으로 노출한다. 여기에 secret을 넣으면 브라우저 번들에 포함된다.

| 변수 | 용도 |
|---|---|
| `NEXT_PUBLIC_AUTH_API` | Auth API base URL |
| `NEXT_PUBLIC_USER_API` | User API base URL |
| `NEXT_PUBLIC_VM_API` | VM API base URL |
| `NEXT_PUBLIC_OPS_API` | Ops API base URL |
| `NEXT_PUBLIC_ADMIN_API` | 관리자 API base URL. 없으면 User·VM URL로 fallback |
| `ADMIN_DOMAIN` | 서버 middleware가 ControlBox 도메인을 구분하는 Host |

개발용 `.env.local` 예시는 다음과 같다. 실제 주소는 환경에 맞게 바꾼다.

```dotenv
NEXT_PUBLIC_AUTH_API=http://localhost:8080
NEXT_PUBLIC_USER_API=http://localhost:8081
NEXT_PUBLIC_VM_API=http://localhost:8082
NEXT_PUBLIC_OPS_API=http://localhost:8083
ADMIN_DOMAIN=admin.localhost:3000
```

로컬에서 `NEXT_PUBLIC_ADMIN_API`를 생략하면 관리자 User API는 `NEXT_PUBLIC_USER_API`, 관리자 VM API는 `NEXT_PUBLIC_VM_API`로 각각 fallback한다. Caddy를 사용하는 배포 환경은 다음처럼 사용자 API를 Portal origin에 모으고 관리자 API를 ControlBox origin으로 지정한다.

```dotenv
NEXT_PUBLIC_AUTH_API=https://portal.gamjabox.cloud
NEXT_PUBLIC_USER_API=https://portal.gamjabox.cloud
NEXT_PUBLIC_VM_API=https://portal.gamjabox.cloud
NEXT_PUBLIC_OPS_API=https://portal.gamjabox.cloud
NEXT_PUBLIC_ADMIN_API=https://admin.gamjabox.cloud
ADMIN_DOMAIN=admin.gamjabox.cloud
```

`ADMIN_DOMAIN`에는 scheme과 path 없이 Host만 넣는다. `NEXT_PUBLIC_*`는 빌드 시 브라우저 번들에 고정되므로 값을 바꾼 뒤 Portal 이미지를 반드시 재빌드하고, secret은 절대 넣지 않는다. 루트 Compose용 전체 예시는 `env.example`을 기준으로 한다.

## 로컬 실행

요구사항은 Node.js 22와 npm이다.

```bash
cd Frontend/portal
npm ci
npm run dev
```

기본 주소는 `http://localhost:3000`이다. 인증 이후 기능을 확인하려면 Auth·User·VM·Ops API와 각 서비스의 CORS 설정이 포털 origin을 허용해야 한다.

## 검증 명령

```bash
npm run lint
npx tsc --noEmit
npm run blueprint:check
npm run build
```

`npm run build`는 `prebuild`에서 Blueprint registry 정합성 검사를 먼저 실행한다. manifest와 생성 결과가 다르면 빌드가 실패하므로 다음 명령으로 정본을 갱신한다.

```bash
npm run blueprint:generate
npm run blueprint:check
```

## Auto Preview Runtime

`components/preview-runtime/blueprints/manifests/component-manifest.json`이 컴포넌트 계약의 단일 정본이다.

```text
component manifest
  → generate-blueprint-registry.mjs
  → TypeScript registry·계약 검증
  → 포털 PreviewPageRenderer
  → Ops syncPreviewTemplate
  → VM 배포용 Vite + React Runtime
```

- 신규 Blueprint Part는 manifest와 구현을 함께 추가한다.
- 렌더러에 componentId별 거대한 조건 분기를 다시 만들지 않는다.
- API 배열이나 선택 데이터는 비어 있거나 누락될 수 있으므로 Runtime 경계에서 정규화한다.
- 포털에서만 작동하고 배포본에서 깨지는 것을 막기 위해 Ops의 `clean build`도 함께 수행한다.

## UI와 반응형 원칙

- 공통 모달은 `components/ui/modal.tsx`를 사용해 진입·퇴장 애니메이션과 포커스 처리를 통일한다.
- 페이지 본문, Auto Preview API 패널, 시나리오 패널처럼 의미가 다른 영역의 스크롤을 분리한다.
- 320/375/768/1024/1440px에서 문서·테이블·코드 블록의 가로 넘침을 확인한다.
- 버튼처럼 보이는 Blueprint 요소는 실제 Flow, modal, navigation 중 하나에 연결하거나 비활성 상태를 명확히 표현한다.
- `prefers-reduced-motion` 사용자는 과도한 이동 애니메이션 없이 기능을 사용할 수 있어야 한다.

## 프로덕션 빌드와 배포

Next.js standalone 결과는 다음 파일을 포함해 실행한다.

```text
.next/standalone
.next/static
public
```

컨테이너 엔트리포인트는 `node server.js`, 내부 포트는 3000을 사용한다.

- `develop`의 `Frontend/portal/**` 변경은 `deploy-portal.yml`로 개발 VM에 배포된다.
- `main` 변경은 `deploy-main-portal.yml`로 운영 VM에 배포된다.
- 배포 후 `/` 응답이 실패하면 보존된 직전 이미지로 자동 롤백한다.
- `NEXT_PUBLIC_*` 값은 Next.js 빌드 시점에 번들되므로 런타임 환경변수만 바꾸고 이미지를 재빌드하지 않으면 반영되지 않는다.

## 자주 확인할 문제

| 증상 | 확인 항목 |
|---|---|
| 잠시 후 로그인 화면으로 이동 | Auth 쿠키 domain/SameSite, refresh 응답 코드, 다중 탭 동기화 |
| API가 잘못된 서버로 요청됨 | `NEXT_PUBLIC_*` 빌드 인자와 이미지 재빌드 여부 |
| ControlBox가 404 | `ADMIN_DOMAIN`과 실제 Host 헤더 일치 여부 |
| SSE 연결이 반복 종료 | 단기 티켓 발급, Caddy buffering, 대상 API URL |
| Preview 버튼이 반응하지 않음 | Blueprint Flow/action binding과 정적 검증 경고 |
| 포털과 배포 Preview 결과가 다름 | manifest 생성, `blueprint:check`, Ops `syncPreviewTemplate` 결과 |
