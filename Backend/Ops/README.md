# Ops Service

GamjaBox VM 내부의 SSH 기반 운영 기능과 배포 파이프라인, GitHub 자동 배포, Docker 관리, 파일 브라우저, DB 백업, Auto Preview 엔진을 담당하는 Spring Boot 서비스다.

> 전체 시스템 구성은 [프로젝트 루트 README](../../README.md)를 참고한다.

## 책임 범위

- 브라우저 SSH 터미널(WebSocket + 일회용 티켓)
- VM 파일 조회·편집·업로드·다운로드·미디어 스트리밍
- Docker 설치와 컨테이너·이미지·네트워크·Compose 관리
- Git/Compose/AI 기반 배포, 헬스체크, 롤백, 대상 삭제
- GitHub App 저장소 연결과 push 자동 재배포
- PostgreSQL/MySQL/MongoDB/Redis 백업
- OpenAPI 기반 Auto Preview 분석·조립·실행·VM 배포
- Preview 회귀 Suite와 CI 실행 기록
- VM 관리 키 암호화 저장과 SSH 준비 검증

## SSH 실행 경계

Ops는 VM 서비스에서 사용자 권한과 내부 IP를 확인한 다음 관리 키로 SSH에 접속한다. 명령 입력을 문자열로 무제한 연결하지 않고 기능별 검증·escaping과 고정된 실행 단계를 사용한다.

```text
사용자 요청
  → VM 서비스에서 권한·상태·IP 확인
  → Ops 관리 키 복호화
  → SSH 세션 생성
  → 제한된 원격 작업 수행
  → 감사 로그·진행 상태·오류 기록
```

## 배포 파이프라인

```text
저장소/Compose 입력
  → 저장소 분석 또는 Compose 감지
  → 결정론적 스펙 생성, 필요한 경우만 AI 보조
  → 스펙 검증·렌더링
  → bare Git + release worktree 준비
  → Docker 이미지 빌드·Compose 기동
  → 헬스체크
  → 성공 시 current 전환 / 실패 시 직전 이미지 롤백
  → VM 서비스에 Cloudflare 노출 라우트 동기화
```

- 배포 대상별 appId, Compose project, 저장소, release, 이미지, 락을 격리한다.
- 같은 대상의 연속 push는 최신 commit SHA 하나만 pending으로 유지한다.
- 대상 삭제는 컨테이너, 전체 이미지 이력, 저장소·release, 심볼릭 링크와 라우트를 정리하고 이력은 감사 목적으로 남긴다.
- Compose 다중 서비스의 외부 진입점은 분석 결과에 따라 Caddy 라우터를 계획할 수 있다.

## Auto Preview

Auto Preview는 OpenAPI URL 또는 JSON/YAML 원문과 서비스 설명·문서 URL을 받아 다음 결과를 만든다.

1. OpenAPI 정규화와 서버 URL 보정
2. Capability, Actor, Entity, Goal 추출
3. 다중 API Scenario와 데이터 Binding 계획
4. 페이지·모달·Flow와 Blueprint Parts 조립
5. 포털 공용 Runtime에서 실제 API 호출과 검증
6. 동일 Runtime 소스를 사용한 Vite + React 배포 아티팩트 생성

포털의 `components/preview-runtime`과 Ops 배포본은 별도 구현이 아니다. `syncPreviewTemplate` Gradle 작업이 포털 소스를 Ops 리소스로 복사한다. Blueprint manifest도 `syncBlueprintManifest`가 단일 정본을 동기화한다.

## API 영역

| 영역 | 대표 경로 | 설명 |
|---|---|---|
| 터미널 | `/ops/{vmId}/terminal-ticket`, `/ws/terminal/**` | 웹 SSH 티켓과 WebSocket |
| 파일 | `/ops/{vmId}/files/**` | 파일 CRUD·다운로드·스트리밍 |
| Docker | `/ops/{vmId}/docker/**` | 설치·컨테이너·이미지·네트워크·Compose |
| 배포 | `/ops/{vmId}/deployments/**` | 생성·AI 스펙·Compose 분석·롤백·로그 |
| 배포 대상 | `/ops/{vmId}/deployment-targets/**` | 자동 배포·포트 연결·재배포·삭제 |
| GitHub | `/ops/github/**`, `/ops/webhooks/github` | App 설치·저장소·웹훅 |
| 백업 | `/ops/{vmId}/backups` | DB 백업 생성·조회 |
| Preview | `/ops/preview/**`, `/ops/{vmId}/preview/deploy` | 분석·검수·조립·배포 |
| 회귀 Suite | `/ops/preview/regression-suites/**` | Suite와 실행 결과 |
| 내부 SSH | `/internal/vms/{vmId}/management-key`, `/internal/vms/{vmId}/ssh-readiness` | 관리 키와 준비 상태 |

## 데이터와 외부 의존성

- PostgreSQL/JPA: 배포·대상·GitHub 설치·관리 키·Preview·회귀 기록
- Redis: 일회용 티켓, 락, AI 캐시
- VM 서비스: VM 권한 문맥, IP, 포트와 배포 라우트
- User 서비스: 사용자 플랜 확인
- GitHub App API: installation token, 저장소, webhook
- OpenAI API: 불확실한 배포 스펙과 Preview 의미 계획 보조
- Elasticsearch: Blueprint 검색 인덱스(기능 플래그로 제어)

## 환경변수

| 분류 | 변수 | 용도 |
|---|---|---|
| DB | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL 연결 |
| Redis | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | 티켓·락·캐시 |
| 인증 | `AUTH_SERVER_URL`, `OPS_SERVICE_CLIENT_SECRET` | JWT·서비스 인증 |
| 서비스 연동 | `VM_SERVICE_URL`, `USER_SERVICE_URL` | VM 문맥·라우트와 플랜 조회 |
| SSH | `OPS_KEY_ENCRYPTION_SECRET`, `VM_SSH_USERNAME` | 관리 키 AES-GCM 암호화와 접속 사용자 |
| GitHub App | `GITHUB_APP_ID`, `GITHUB_APP_CLIENT_ID`, `GITHUB_APP_CLIENT_SECRET`, `GITHUB_APP_PRIVATE_KEY`, `GITHUB_APP_SLUG`, `GITHUB_WEBHOOK_SECRET` | App 인증·설치·웹훅 검증 |
| AI | `OPENAI_API_KEY`, `AI_MODEL_STANDARD`, `AI_MODEL_ESCALATED` | 모델 호출과 난이도별 모델 선택 |
| Blueprint 검색 | `BLUEPRINT_SEARCH_ENABLED`, `BLUEPRINT_SEARCH_INDEX`, `BLUEPRINT_SEARCH_REINDEX_ON_STARTUP` | 검색 기능과 인덱스 관리 |
| Elasticsearch | `ELASTICSEARCH_URL`, `ELASTICSEARCH_USERNAME`, `ELASTICSEARCH_PASSWORD` | Blueprint 검색 연결 |
| 파일 제한 | `OPS_FILE_BROWSER_MAX_EDIT_SIZE_BYTES`, `OPS_FILE_BROWSER_MAX_UPLOAD_SIZE_BYTES` | 편집·업로드 최대 크기 |
| 포털 | `PORTAL_ORIGIN` | CORS와 GitHub 연결 복귀 주소 |

GitHub App ID와 Client ID는 서로 다른 값이다. installation token 404가 발생하면 App ID, private key가 해당 installation의 App과 일치하는지 먼저 확인한다.

## 로컬 실행과 검증

요구사항은 JDK 17, PostgreSQL, Redis다. Auto Preview 리소스 동기화를 위해 저장소 전체 구조에서 빌드해야 한다.

```bash
cd Backend/Ops
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

```bash
./gradlew test
./gradlew clean build
```

```bash
# 포털 Blueprint 정본까지 함께 검증
cd ../../Frontend/portal
npm run blueprint:check
```

서비스 상태는 `/actuator/health`에서 확인한다.

## 배포

- `develop`의 `Backend/Ops/**` 변경은 `deploy-ops.yml`을 통해 개발 VM에 배포된다.
- `main` 변경은 `deploy-main-ops.yml`을 통해 운영 VM에 배포된다.
- Docker 빌드 context는 Ops 단독 폴더가 아니라 포털 Runtime을 포함하는 저장소 구조를 보존해야 한다.
- 헬스체크 실패 시 보존된 직전 이미지로 자동 롤백한다.

## 운영 시 주의점

- SSH 명령 시간 초과가 원격 프로세스 실패를 뜻하지는 않는다. 긴 apt/dpkg 작업은 단계별 제한과 상태 확인을 사용한다.
- Docker 설치는 dpkg 잠금과 cloud-init 때문에 수 분 걸릴 수 있으며 HTTP 요청 스레드를 점유하지 않는다.
- DB check constraint에 enum 값을 추가할 때 Java enum과 `schema.sql` 제약을 반드시 함께 갱신한다.
- `OPS_KEY_ENCRYPTION_SECRET`, GitHub private key, webhook secret, OpenAI key를 로그나 배포 스펙에 포함하지 않는다.
- Blueprint manifest나 Runtime을 바꾼 뒤에는 포털 검사와 Ops 빌드를 모두 실행한다.
