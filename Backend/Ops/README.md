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
- 플랫폼 관리형 Auto Preview Worker 레지스트리·조정·TTL Runtime 정리
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

배포 대상 카드의 **구성 편집**에서 최종 Compose, 환경변수와 공개 주소를 변경할 수 있다. 저장은 실행 중인 컨테이너를 바꾸지 않으며 다음 재배포 또는 GitHub push 배포에 적용한다. 같은 설정을 동시에 편집하면 오래된 저장 버전을 거부한다.

AI 생성 결과도 최종 YAML을 직접 수정할 수 있으며 배포 요청은 편집한 YAML을 사용한다. 스펙에서 다시 생성하거나 연결 설정을 적용하면 해당 부분을 다시 작성하므로 결과를 확인한다. 환경 파일은 받을 서비스를 선택하면 `env_file`로 연결되고, 서비스 선택 없는 `.env`는 Compose 치환에 사용된다. 기존 `environment`의 같은 키는 파일보다 우선하며 프론트 빌드 시점 변수는 별도 빌드 설정이 필요하다.

서비스 연결 구성도에서 앱, 내부 포트, 기본 진입점, 경로와 도메인 연결을 선택하고 **연결 설정 적용**으로 Compose에 반영한다. 생성된 Caddyfile도 편집할 수 있다. 정적 검증은 서비스·의존성·변수·호스트 바인딩을 확인하며 실제 파일·빌드·컨테이너 상태는 배포 단계에서 검증한다. Caddy는 시작 전 설정을 검증하고 각 서비스의 컨테이너 상태와 별도 HTTP 헬스체크를 확인한다. 이 검증만으로 모든 애플리케이션의 기능적 정상 동작을 보장하지는 않는다.

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
- 원격 Git 작업은 시간·메모리·CPU·파일 크기·파일 디스크립터 한도와 Git 병렬성 제한을 사용한다. 사용자 전체의 실행 중인 스레드를 세는 `RLIMIT_NPROC`는 별도로 낮추지 않고 VM의 기존 한도를 상속한다. 고정 `--nproc=64`는 이미 앱이 실행 중인 VM에서 정상적인 `remote-https` 생성까지 막을 수 있다.
- 같은 대상의 연속 push는 최신 commit SHA 하나만 pending으로 유지한다.
- 대상 삭제는 컨테이너, 전체 이미지 이력, 저장소·release, 심볼릭 링크와 라우트를 정리하고 이력은 감사 목적으로 남긴다.
- Compose 다중 서비스의 외부 진입점은 분석 결과에 따라 Caddy 라우터를 계획할 수 있다.
- 기존 Dockerfile에 숫자 `EXPOSE`가 있으면 프레임워크 기본 포트나 이전 화면 입력값보다 우선한다. 같은 포트를 생성 Compose의 바인딩, Caddy upstream, 헬스체크에 일관되게 사용한다.
- VM 라우트 동기화가 4xx를 반환하면 Ops는 원격 status와 안전하게 정제한 message를 보존해 유효성·권한·VM 없음·CNAME 충돌을 구분한다. 네트워크·5xx는 `DEPLOYMENT_ROUTE_SYNC_FAILED`로 표기하며 라우트 오류를 일괄적인 “VM 정보 조회 실패”로 바꾸지 않는다.

## Auto Preview

Auto Preview는 OpenAPI URL 또는 JSON/YAML 원문과 서비스 설명·문서 URL을 받아 다음 결과를 만든다.

1. OpenAPI 정규화와 서버 URL 보정
2. Capability, Actor, Entity, Goal 추출
3. 다중 API Scenario와 데이터 Binding 계획
4. 페이지·모달·Flow와 Blueprint Parts 조립
5. 포털 공용 Runtime에서 실제 API 호출과 검증
6. 동일 Runtime 소스를 사용한 Vite + React 배포 아티팩트 생성

포털의 `components/preview-runtime`과 Ops 배포본은 별도 구현이 아니다. `syncPreviewTemplate` Gradle 작업이 포털 소스를 Ops 리소스로 복사한다. Blueprint manifest도 `syncBlueprintManifest`가 단일 정본을 동기화한다.

VM이 없는 사용자는 `/ops/preview/deploy`로 관리형 타겟을 선택한다. Ops의 `SystemWorker` 레지스트리가 `AUTO_PREVIEW` 역할 전용 VM을 조정하고, Preview마다 고유 포트·컨테이너·Compose project·호스트명을 배정한다. 컨테이너는 0.5 CPU/256MB로 제한하며 FREE는 6시간, PRO는 24시간 후 자동 정리한다. 일반 사용자 응답에는 worker ID, VMID, node, 내부 IP, SSH 정보를 포함하지 않는다.

### 사용자 흐름 프리뷰와 검증

Auto Preview는 프론트가 미완성인 백엔드의 임시 테스트 화면이다. 서버 계약과 설명에서 의도를 추론하고, 실제 사용자의 목표를 Scenario로 계획한 뒤 화면과 입력·확인 모달을 조립한다. 개발자는 사용자처럼 조작하면서 API 연결, 응답 전달, 성공 조건과 부족한 계약을 확인하고 같은 Runtime을 배포해 테스트 URL을 공유한다.

- 제품 화면의 메뉴는 추론된 Scenario와 실제 미배치 목록 Capability에서 구성한다. 업종별 고정 메뉴·가짜 브랜드·프로필은 생성하지 않으며 업종 추론은 데이터 표현과 테마 선택에 사용한다.
- 실제 응답만 목록에 표시한다. 로딩, 빈 목록, 요청 실패와 목록 API 없음은 구분하며 샘플 항목으로 빈 응답을 메우지 않는다. 여러 목록을 쓰는 흐름은 조회 대상을 선택해 서로 다른 리소스 ID가 섞이지 않게 한다.
- 사용자 흐름 옆에서 단계 의도, 연결된 API, 미실행/호출 중/통과/실패와 컴파일 진단을 확인한다. 연결 검증을 실제 동작 성공으로 표시하지 않는다. 기존 Inspector에서 요청·응답·상태·검증 결과와 원시 요청 본문을 확인할 수 있다.
- AI의 `coverageGaps`는 사용자 목표를 시험하기 위해 추가 확인할 계약을 제안한다. 존재하지 않는 API를 실행하거나 로컬 성공 단계로 대신하지 않으며, 문서에서 보이지 않는 기능을 서버 구현 누락으로 단정하지 않는다. 제안은 제한된 비실행 진단으로 저장되고 배포본에도 전달된다.
- 실행은 실제 선택된 항목의 ID를 사용한다. 임의의 첫 항목을 자동 선택하거나 선택한 ID를 모든 경로 변수에 복사하지 않는다. 후속 단계에서 실패하면 상태를 유지하고 실패한 단계부터 재시도해 앞서 성공한 생성/변경을 반복하지 않는다. 실패한 변경 요청 자체의 재시도에는 백엔드의 멱등성 보장이 별도로 필요하다.
- 포털과 관리형/사용자 VM 배포본은 같은 Scenario·진단·수정된 Page Plan을 전달받아 공용 Runtime에서 조립한다. 배포 아티팩트의 파츠 선택도 공통 선택기를 사용한다.

다음 품질 검수는 특정 업종의 흐름을 하드코딩하는 방식이 아니라, 서로 다른 서버의 OpenAPI와 설명을 입력해 다른 사용자 목표와 화면이 자동으로 생성되는지 확인한다. test-api의 커머스·예약·커뮤니티를 사례로 사용하고, 실제 API 계약에 근거한 정상·빈 결과·실패·수정/재시도 흐름을 프리뷰와 배포본에서 비교한다. 모델 제안의 의미 품질, 실제 백엔드 실행, 브라우저 UI 검증은 각각 별도의 증거로 관리한다.

## API 영역

| 영역 | 대표 경로 | 설명 |
|---|---|---|
| 터미널 | `/ops/{vmId}/terminal-ticket`, `/ws/terminal/**` | 웹 SSH 티켓과 WebSocket |
| 파일 | `/ops/{vmId}/files/**` | 파일 CRUD·다운로드·스트리밍 |
| Docker | `/ops/{vmId}/docker/**` | 설치·컨테이너·이미지·네트워크·Compose |
| 배포 | `/ops/{vmId}/deployments/**` | 생성·AI 스펙·Compose 분석·롤백·로그 |
| 배포 대상 | `/ops/{vmId}/deployment-targets/**` | 자동 배포·포트 연결·재배포·삭제 |
| GitHub | `/ops/github/**`, `/ops/webhooks/github` | App 설치·저장소·웹훅 |
| 백업 | `/ops/{vmId}/backups`, `/ops/{vmId}/backups/{backupId}/download` | DB 백업 생성·조회·검증·전용 다운로드 |
| Preview | `/ops/preview/**`, `/ops/{vmId}/preview/deploy` | 분석·검수·조립·관리형 또는 사용자 VM 배포 |
| 시스템 워커 관리 | `/admin/system-workers/auto-preview/**` | 구성·전원·존재 확인·유실 VM 재생성·Reconcile·Repair·일회용 콘솔 티켓 |
| 관리자 배포 운영 | `/admin/deployment-operations/**` | 전체 대상·자동 재배포·최근 로그·고아 정리 이력 조회와 수동 조정 |
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

### 배포 조회·복구 최적화

- 관리자 배포 개요는 최근 deployment 100건의 최신 event만 PostgreSQL `DISTINCT ON` 으로 일괄 조회한다. event 전체를 가져와 JVM에서 걸러내지 않는다.
- 관리형 Preview 포트 할당은 점유 엔티티 전체를 로드하지 않고 `generate_series`+anti-join으로 첫 빈 포트 하나만 반환한다.
- 30초 자동 재배포 재시도는 활성 대상 전체가 아니라 `latest_requested_revision`에 해당하는 deployment가 아직 없는 ID/revision projection만 조회한다.
- Ops 재시작 시 대상별 최신 deployment를 개별 조회하던 N+1을 제거하고, 활성 포인터가 변경되어야 하는 행만 set-based 쿼리로 선별한다. rollback의 이전 배포도 일괄 조회한다.
- 관리형 Preview 목록은 Preview마다 deployment를 다시 조회하지 않고 필요한 deployment ID를 한 번에 조회해 상태를 갱신한다.
- 최근순, STOPPING 복구, legacy 성공 이력, Git push 미완료, target/revision 검사에 맞춘 복합/partial 인덱스를 사용하며 동일 prefix의 중복 인덱스는 제거한다.

서비스 간 client-credentials 토큰은 Auth가 반환한 만료 시각보다 최대 30초 먼저 갱신하도록 메모리에 보관한다. 동시에 여러 내부 호출이 발생해도 발급 요청은 하나로 직렬화하므로 Auth 호출과 JWT 서명 비용이 내부 API 요청 수만큼 반복되지 않는다.

### 배포 이벤트 SSE와 DB 풀 격리

Ops는 `spring.jpa.open-in-view=false`로 요청 수명과 EntityManager 수명을 분리한다. 배포 이벤트 SSE를 열 때 과거 이벤트 조회가 끝나면 JDBC 커넥션을 즉시 풀로 반환하며, 장기 스트림이 커넥션을 점유하지 않는다.

- 스트림은 5분 후 정상 종료되고 포털이 마지막 `afterSequence`부터 재연결한다.
- 15초 comment heartbeat로 이벤트가 없는 동안에도 프록시·브라우저 단절을 감지한다.
- 완료, timeout, 전송 오류와 재생 조회 실패 시 emitter를 구독 목록에서 제거한다.
- 배포 단계는 Git mirror·fetch·worktree, 파일 전송, Compose 검증, 서비스별 빌드, Compose up, 헬스체크 시도, 라우트, 롤백으로 나눠 이벤트를 남긴다.
- 원격 명령 결과는 안전한 작업명·대상·종료 코드·소요 시간과 stdout/stderr 각 마지막 20,000자를 구조화 payload로 저장한다. 실제 명령 문자열은 저장하지 않는다.
- Authorization, GitHub token, `password`/`token`/`secret`/`api_key` 형태의 할당문은 백엔드 저장 전과 프론트 표시 전에 각각 마스킹한다.
- 운영 Hikari는 60초 leak detection을 사용해 비정상 장기 점유의 커넥션 획득 스택을 기록한다.
- 풀 크기 증가는 근본 해결로 사용하지 않는다. PostgreSQL은 idle인데 Hikari active가 계속 증가하면 장기 요청과 OSIV/트랜잭션 경계를 먼저 확인한다.

### Auto Preview Worker 유실 복구

ControlBox의 일반 조회는 Ops DB에 마지막으로 조정된 Worker 상태만 즉시 반환한다. 브라우저의 15초 폴링마다 Proxmox Guest Agent IP 대기를 반복하지 않으며, 실제 VM·Runtime 상태 확인은 기본 60초 주기 조정과 관리자가 누른 Reconcile에서 수행한다. 따라서 평상시 조회는 DB 1회로 끝나고, 외부에서 VM을 직접 삭제한 상태는 자동 조정 기준 최대 약 1분 뒤 또는 Reconcile 직후 반영된다.

조정 시 VM이 없으면 저장 상태가 `ACTIVE`, `DEGRADED`, `STOPPED`, `ERROR`였더라도 `MISSING`으로 갱신하고 내부 IP를 제거한다. 재생성 API도 요청 시점의 Proxmox 상태를 다시 검사하므로 DB 상태가 늦게 갱신된 경우에도 실제 VM이 없으면 재생성할 수 있다. 단, `PROVISIONING` 상태이거나 예약 VMID에 VM이 존재하면 중복 생성을 막는다. VM 서비스 조회 자체가 실패한 경우에는 부재로 간주하지 않는다.

### 사용자 배포 대상 고아 조정

Ops는 활성 배포 대상을 기본 5분마다 VM 서비스 정본과 대조한다. 이때 암호화된 Compose 등 넓은 target 엔티티 대신 target ID와 VM ID projection만 읽고, 중복을 제거한 VM ID를 최대 500개씩 VM 서비스에 일괄 조회한다. VM 서비스가 정상 응답한 묶음에서 존재하지 않는 ID만 고아로 판정하며 타임아웃, 네트워크 오류, 5xx가 발생한 묶음은 모두 보류한다. 잘못된 UUID 형식의 레거시 데이터도 자동 삭제하지 않는다. 자동 재배포 실행 중 개별 VM 조회가 명확한 `404 VM_NOT_FOUND`를 반환하면 Redis pending을 제거해 30초 재시도 루프를 멈춘다.

- 연결된 `deployments`, 관리형 프리뷰, 회귀 Suite가 모두 없고 배포 락도 없으면 `deployment_targets`를 완전 삭제한다.
- 배포·프리뷰·회귀 데이터가 하나라도 있거나 실행 락이 남아 있으면 `active=false`, `auto_deploy_enabled=false`, `orphaned_at`, `orphan_reason`으로 격리한다.
- hard delete와 격리 모두 `deployment_orphan_cleanup_events`에 소유자·VM·대상·판정 근거를 감사 이벤트로 남긴다.
- `DeploymentEntity`와 `deployment_events`는 사용자 배포 감사·장애 분석을 위해 자동 삭제하지 않는다.
- ControlBox **배포 운영** 화면에서 전체 사용자 대상, 활성 자동 재배포, 최근 배포와 배포별 이벤트 로그(최대 1,000건), 격리 대상과 정리 이력을 확인하고 즉시 조정을 실행할 수 있다.

## 환경변수

| 분류 | 변수 | 용도 |
|---|---|---|
| DB | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL 연결 |
| Redis | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | 티켓·락·캐시 |
| 인증 | `AUTH_SERVER_URL`, `OPS_SERVICE_CLIENT_SECRET` | JWT·서비스 인증 |
| 서비스 연동 | `VM_SERVICE_URL`, `USER_SERVICE_URL` | VM 문맥·라우트와 플랜 조회 |
| SSH·티켓 암호화 | `OPS_KEY_ENCRYPTION_SECRET`, `VM_SSH_USERNAME` | 관리 키·Redis 스트림 티켓 AES-GCM 암호화와 접속 사용자 |
| Git 방어 | `OPS_GIT_ALLOWED_HOSTS`, `OPS_GIT_LOCAL_EGRESS_PROXY_URL`, `OPS_GIT_REMOTE_EGRESS_PROXY_URL`, `OPS_REPO_ANALYSIS_MAX_CLONE_SIZE_BYTES`, `OPS_REPO_ANALYSIS_MAX_PROCESSES` | clone allowlist·egress proxy와 분석용 clone 자원 한계. 프로세스 값은 Ops JVM의 현재 task 수에 더하는 clone 전용 여유 |
| 백업 보호 | `OPS_BACKUP_ENCRYPTION_SECRET`, `OPS_BACKUP_RETENTION_DAYS`, `OPS_BACKUP_MAX_FILES_PER_VM` | AES-256-GCM 암호화 키와 보관 기간·개수 |
| GitHub App | `GITHUB_APP_ID`, `GITHUB_APP_CLIENT_ID`, `GITHUB_APP_CLIENT_SECRET`, `GITHUB_APP_PRIVATE_KEY`, `GITHUB_APP_SLUG`, `GITHUB_WEBHOOK_SECRET` | App 인증·설치·웹훅 검증 |
| AI | `OPENAI_API_KEY`, `AI_MODEL_STANDARD`, `AI_MODEL_ESCALATED` | 모델 호출과 난이도별 모델 선택 |
| Blueprint 검색 | `BLUEPRINT_SEARCH_ENABLED`, `BLUEPRINT_SEARCH_INDEX`, `BLUEPRINT_SEARCH_REINDEX_ON_STARTUP` | 검색 기능과 인덱스 관리 |
| Elasticsearch | `ELASTICSEARCH_URL`, `ELASTICSEARCH_USERNAME`, `ELASTICSEARCH_PASSWORD` | Blueprint 검색 연결 |
| 파일 제한 | `OPS_FILE_BROWSER_MAX_EDIT_SIZE_BYTES`, `OPS_FILE_BROWSER_MAX_UPLOAD_SIZE_BYTES` | 편집·업로드 최대 크기 |
| 포털 | `PORTAL_ORIGIN`, `ADMIN_ORIGIN` | 사용자 콘솔·ControlBox worker 콘솔 WebSocket Origin allowlist |
| 로컬 개발 | `LOCAL_DEV_ORIGINS` | 개발 서버 Ops 터미널에 추가로 허용할 localhost Origin 목록(운영은 빈 값) |
| 관리형 Preview | `SYSTEM_WORKER_AUTO_PREVIEW_*`, `MANAGED_PREVIEW_PORT_*`, `MANAGED_PREVIEW_*_TTL_HOURS` | 전용 워커 사양·VMID·포트 풀·플랜별 TTL |
| 고아 배포 조정 | `OPS_DEPLOYMENT_ORPHAN_RECONCILE_INTERVAL_MS` | VM 정본과 활성 배포 대상을 대조하는 주기(기본 300000ms) |
| API 문서 | `OPENAPI_SERVER_URL` | `docs` 프로파일의 Try it out 대상 API Gateway URL |

`OPS_KEY_ENCRYPTION_SECRET`와 `OPS_BACKUP_ENCRYPTION_SECRET`은 각각 `openssl rand -base64 24`로 생성한 서로 다른 UTF-8 32바이트 값을 사용한다. 관리 SSH 키나 암호화 백업이 생성된 뒤에 이 값을 변경하면 기존 데이터를 복호화할 수 없으므로 서버 재배포 때도 동일한 값을 유지한다.

GPT 호출은 배포 스펙 생성·Compose 검수·Preview 시나리오 계획·페이지 검수·수정안 생성·파츠 추천 모두 공통 모델 설정을 사용한다. 개발/운영 기본값과 `env.example`의 `AI_MODEL_STANDARD`, `AI_MODEL_ESCALATED`는 모두 `gpt-6.1-sol`이다. 난이도별 `low`/`medium` 추론 강도와 제한된 교정 흐름은 유지하며, 생성 결과 캐시는 두 모델 설정을 키에 포함해 이전 모델 결과를 재사용하지 않는다. 기존 `.env`에 모델 값이 있으면 기본값보다 우선하므로 두 변수를 함께 갱신해야 한다. Responses API와 구조화 출력 계약은 유지한다. [공식 모델 문서](https://developers.openai.com/api/docs/models/gpt-6.1-sol), [GPT-6 전환 안내](https://developers.openai.com/api/docs/guides/latest-model?model=gpt-6.1-sol)를 기준으로 적용했다.

`OPS_GIT_LOCAL_EGRESS_PROXY_URL`은 Ops 컨테이너가 저장소 분석 clone에 사용하는 주소이며 기본값 `http://ops-git-egress-proxy:3128`을 그대로 사용할 수 있다. `OPS_GIT_REMOTE_EGRESS_PROXY_URL`은 사용자 VM 안에서 실행되는 Git이 도달할 수 있는 별도 LAN proxy가 있을 때만 설정한다. 없으면 비워 두며, Docker 내부 서비스명 주소를 넣으면 사용자 VM에서는 해석되지 않는다.

GitHub App ID는 숫자이고 OAuth Client ID는 보통 `Iv1...` 형식인 서로 다른 값이다. `GITHUB_APP_PRIVATE_KEY`에는 GitHub App에서 발급한 PEM 전체를 `\n` 문자로 연결해 넣고, OAuth client secret과 webhook secret도 각각 Dashboard 값으로 설정한다. installation token 404가 발생하면 App ID와 private key가 해당 installation의 App과 일치하는지 먼저 확인한다. `PORTAL_ORIGIN`은 브라우저가 실제로 접속하는 `https://portal.gamjabox.cloud` 같은 origin만 넣고 path는 포함하지 않는다.

Blueprint 검색은 기본 Registry fallback이므로 Elasticsearch가 없으면 `BLUEPRINT_SEARCH_ENABLED=false`를 유지한다. 사용할 때만 연결 URL과 인증 정보를 설정하고, 시작 시 재색인은 관리된 단일 인스턴스에서만 활성화한다. 전체 값 예시는 루트 `env.example`을 기준으로 하며 모든 `CHANGE_ME_*`를 배포 전에 교체한다.

배포 환경에서 `prod,docs` 프로파일을 활성화하면 Ops가 Swagger UI WebJar 자산과 자체 OpenAPI JSON을 제공한다. 루트 `api-docs-ui`의 GamjaBox 전용 콘솔이 이 자산과 Auth·User·VM·Ops 스펙을 조합하며, 다른 세 서비스는 OpenAPI JSON만 제공한다. 콘솔은 서버·검색·스펙 통계를 합친 `API Reference`와 카테고리/검색을 제공하는 기능별 `API Flows`를 분리해 제공한다. 플로우 단계는 현재 OpenAPI 경로와 대조하고 클릭 시 Reference의 해당 경로 검색으로 연결된다. Try it out은 문서 도메인의 `/try/*` 동일 출처 프록시를 사용하고 인증 쿠키도 포함한다. 운영은 `prod`만 유지해 문서 엔드포인트를 비활성화한다.

`OpenApiExampleCustomizer`는 공개 비관리자 API의 요청 DTO, path/query/header/cookie 입력과 응답 DTO에 안전한 목업 예시를 자동 보완한다. `ApiResponse<T>` 응답은 실제 `T`를 펼친 완성형 예시로 만들고 `Void`와 성공 메타데이터는 `null` 계약을 반영한다. 명시적 `@Schema(example=...)`는 덮어쓰지 않고 중첩 객체·배열·Map과 `$ref`를 순회하며, `/admin/**`, `/internal/**`와 binary 파일은 제외한다.

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

- `develop`의 Ops 소스, `compose.yaml`, 포털 Runtime 의존 경로 변경은 `deploy-ops.yml`을 통해 개발 VM에 배포된다.
- `main`의 동일한 의존 경로 변경은 `deploy-main-ops.yml`을 통해 운영 VM에 배포된다.
- 포털 의존성은 `lib/types.ts`, `components/ui/**`, `components/preview-runtime/**`이다. 이 경로들이 바뀌면 Ops 이미지를 재빌드하여 배포 앱의 Auto Preview Runtime을 최신 상태로 유지한다.
- Docker 빌드 context는 Ops 단독 폴더가 아니라 포털 Runtime을 포함하는 저장소 구조를 보존해야 한다.
- 헬스체크 실패 시 보존된 직전 이미지로 자동 롤백한다.

## 운영 시 주의점

- Ops 컨테이너는 저장소 분석용 Git 자식 프로세스를 실행하므로 Compose의 `init: true`를 유지한다. Docker init이 PID 1에서 종료된 고아 helper를 회수하며, 이를 제거하면 zombie가 `pids_limit`을 소모해 clone의 `cannot fork()`로 이어질 수 있다.
- SSH 명령 시간 초과가 원격 프로세스 실패를 뜻하지는 않는다. 긴 apt/dpkg 작업은 단계별 제한과 상태 확인을 사용한다.
- Docker 설치는 dpkg 잠금과 cloud-init 때문에 수 분 걸릴 수 있으며 HTTP 요청 스레드를 점유하지 않는다.
- DB check constraint에 enum 값을 추가할 때 Java enum과 `schema.sql` 제약을 반드시 함께 갱신한다.
- DB 백업 생성은 `DEPLOY`, 이력·검증·다운로드는 `BACKUP_READ` 권한을 사용한다. `gamjabox/backups` 경로는 파일 브라우저와 일반 스트리밍 티켓에서 완전히 차단한다.
- DB 비밀번호는 SSH 명령줄이 아닌 0600 임시 credential 파일로만 전달한다. 덤프 stdout은 평문 파일 없이 즉시 AES-256-GCM으로 암호화하고 SHA-256를 기록하며, 생성 직후와 전용 검증 API에서 전체 복호화를 수행한다.
- 성공한 백업은 VM별 보관 기간과 최대 개수를 적용하고, 실패·기간 초과·개수 초과 파일은 정리한다. 복구 리허설은 [DB 백업 복구 런북](docs/DB_BACKUP_RECOVERY.md)을 따른다.
- 기존 평문 백업은 전용 API에서 다운로드하지 않으며 새 백업 생성 시 보관 정책에 따라 순차 정리한다. 즉시 일괄 삭제는 수행하지 않는다.
- 분석용 Git clone은 Squid allowlist proxy를 기본 사용하고 내부·메타데이터 대역을 연결 시점에 차단한다. 사용자 VM의 Git은 해당 VM에서 도달 가능한 `OPS_GIT_REMOTE_EGRESS_PROXY_URL`을 지정했을 때만 프록시를 적용한다.
- private 저장소 분석 clone의 단기 토큰은 Git 런타임 config 환경변수로만 전달한다. `/tmp`는 `noexec`를 유지하며 토큰을 담은 실행 스크립트나 명령줄 인자를 만들지 않는다.
- SVG·HTML·XML은 Ops API origin에서 inline 미리보기하지 않고, 다운로드 응답은 `attachment` + `nosniff` + sandbox CSP를 적용한다.
- `OPS_KEY_ENCRYPTION_SECRET`, GitHub private key, webhook secret, OpenAI key를 로그나 배포 스펙에 포함하지 않는다.
- Blueprint manifest나 Runtime을 바꾼 뒤에는 포털 검사와 Ops 빌드를 모두 실행한다.

## 웹 콘솔 세션 수명

`TerminalSessionRegistry`는 사용자·VM·브라우저 세션 ID 조합으로 SSH/PTY를 보관한다. WebSocket을 닫아도 셸은 유지되므로 콘솔을 떠난 동안 실행된 명령과 출력이 이어진다. 재접속에도 VM 접근 권한을 다시 검증한 새 일회용 티켓과 허용된 Origin이 필요하다. 세션 ID 자체는 인증 수단이 아니며 다른 사용자 또는 VM의 버퍼를 공유하지 않는다. 같은 세션에 두 연결이 붙으면 이전 연결을 닫고 이전 입력·종료 콜백을 무시한다.

`/ws/terminal/{connectionId}?ticket=…&sessionId={UUID}`의 출력은 UTF-8 바이트를 그대로 보존한 binary frame이다. text frame은 `attached`(복원·잘림 여부), `ready`, `heartbeat` 제어 메시지로 사용한다. 클라이언트는 기존처럼 키 입력 원문과 JSON `resize`를 보내고 JSON `heartbeat`에는 응답을 받는다. `sessionId`가 없는 기존 클라이언트에는 text stdout과 연결 종료 시 SSH 정리를 유지해 배포 순서에 따른 호환성을 보존한다.

| 설정 | 기본값 | 동작 |
|---|---:|---|
| `ops.terminal-idle-timeout-minutes` | 30 | 연결 중 실제 입력·출력이 없는 셸 종료 |
| `ops.terminal-session-retention-minutes` | 30 | 연결 분리 시점부터 보관 후 종료; 백그라운드 출력으로 연장하지 않음 |
| `ops.terminal-replay-max-bytes` | 262144 | 세션별 최근 출력 메모리 상한 |
| `ops.terminal-max-sessions` | 128 | 전체 SSH 세션 상한 |
| `ops.terminal-max-sessions-per-user` | 4 | 사용자별 SSH 세션 상한 |

서버는 20초 간격 Ping과 만료 검사를 수행하며 70초간 heartbeat/Pong이 없으면 브라우저 연결만 분리한다. 느린 WebSocket 전송에는 시간·버퍼 상한을 적용한다. heartbeat와 resize는 유휴 시간을 연장하거나 SSH 입력으로 전달하지 않는다. 셸 종료, VM 내부 주소 변경, 보관 만료와 애플리케이션 종료 시 SSH·PTY·출력 버퍼·사용량 카운터를 정리한다. 버퍼와 셸은 Ops 프로세스 메모리에 있으므로 재배포·프로세스 재시작을 넘는 복원 및 여러 Ops replica 간 이동은 지원하지 않는다. 여러 replica를 사용하려면 동일 세션이 동일 Ops 프로세스로 라우팅되어야 한다.
