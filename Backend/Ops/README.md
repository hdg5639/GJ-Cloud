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

VM이 없는 사용자는 `/ops/preview/deploy`로 관리형 타겟을 선택한다. Ops의 `SystemWorker` 레지스트리가 `AUTO_PREVIEW` 역할 전용 VM을 조정하고, Preview마다 고유 포트·컨테이너·Compose project·호스트명을 배정한다. 컨테이너는 0.5 CPU/256MB로 제한하며 FREE는 6시간, PRO는 24시간 후 자동 정리한다. 일반 사용자 응답에는 worker ID, VMID, node, 내부 IP, SSH 정보를 포함하지 않는다.

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

### Auto Preview Worker 유실 복구

ControlBox 조회는 등록된 Worker의 예약 VMID가 Proxmox에 실제로 존재하는지 확인한다. VM이 없으면 저장 상태가 `ACTIVE`, `DEGRADED`, `STOPPED`, `ERROR`였더라도 `MISSING`으로 갱신하고 내부 IP를 제거한다. 재생성 API도 요청 시점의 Proxmox 상태를 다시 검사하므로 DB 상태가 늦게 갱신된 경우에도 실제 VM이 없으면 재생성할 수 있다. 단, `PROVISIONING` 상태이거나 예약 VMID에 VM이 존재하면 중복 생성을 막는다. VM 서비스 조회 자체가 실패한 경우에는 부재로 간주하지 않는다.

### 사용자 배포 대상 고아 조정

Ops는 활성 배포 대상을 기본 5분마다 VM 서비스 정본과 대조한다. VM 서비스가 명확한 `404 VM_NOT_FOUND`를 반환할 때만 고아로 판정하며 타임아웃, 네트워크 오류, 5xx는 정리하지 않는다. 자동 재배포 실행 중에도 같은 404가 확인되면 Redis pending을 제거해 30초 재시도 루프를 멈춘다.

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
| Git 방어 | `OPS_GIT_ALLOWED_HOSTS`, `OPS_GIT_LOCAL_EGRESS_PROXY_URL`, `OPS_GIT_REMOTE_EGRESS_PROXY_URL`, `OPS_REPO_ANALYSIS_MAX_CLONE_SIZE_BYTES` | clone allowlist·egress proxy와 분석용 clone 자원 한계 |
| 백업 보호 | `OPS_BACKUP_ENCRYPTION_SECRET`, `OPS_BACKUP_RETENTION_DAYS`, `OPS_BACKUP_MAX_FILES_PER_VM` | AES-256-GCM 암호화 키와 보관 기간·개수 |
| GitHub App | `GITHUB_APP_ID`, `GITHUB_APP_CLIENT_ID`, `GITHUB_APP_CLIENT_SECRET`, `GITHUB_APP_PRIVATE_KEY`, `GITHUB_APP_SLUG`, `GITHUB_WEBHOOK_SECRET` | App 인증·설치·웹훅 검증 |
| AI | `OPENAI_API_KEY`, `AI_MODEL_STANDARD`, `AI_MODEL_ESCALATED` | 모델 호출과 난이도별 모델 선택 |
| Blueprint 검색 | `BLUEPRINT_SEARCH_ENABLED`, `BLUEPRINT_SEARCH_INDEX`, `BLUEPRINT_SEARCH_REINDEX_ON_STARTUP` | 검색 기능과 인덱스 관리 |
| Elasticsearch | `ELASTICSEARCH_URL`, `ELASTICSEARCH_USERNAME`, `ELASTICSEARCH_PASSWORD` | Blueprint 검색 연결 |
| 파일 제한 | `OPS_FILE_BROWSER_MAX_EDIT_SIZE_BYTES`, `OPS_FILE_BROWSER_MAX_UPLOAD_SIZE_BYTES` | 편집·업로드 최대 크기 |
| 포털 | `PORTAL_ORIGIN`, `ADMIN_ORIGIN` | 사용자 콘솔·ControlBox worker 콘솔 WebSocket Origin allowlist |
| 관리형 Preview | `SYSTEM_WORKER_AUTO_PREVIEW_*`, `MANAGED_PREVIEW_PORT_*`, `MANAGED_PREVIEW_*_TTL_HOURS` | 전용 워커 사양·VMID·포트 풀·플랜별 TTL |
| 고아 배포 조정 | `OPS_DEPLOYMENT_ORPHAN_RECONCILE_INTERVAL_MS` | VM 정본과 활성 배포 대상을 대조하는 주기(기본 300000ms) |

`OPS_KEY_ENCRYPTION_SECRET`와 `OPS_BACKUP_ENCRYPTION_SECRET`은 각각 `openssl rand -base64 24`로 생성한 서로 다른 UTF-8 32바이트 값을 사용한다. 관리 SSH 키나 암호화 백업이 생성된 뒤에 이 값을 변경하면 기존 데이터를 복호화할 수 없으므로 서버 재배포 때도 동일한 값을 유지한다.

`OPS_GIT_LOCAL_EGRESS_PROXY_URL`은 Ops 컨테이너가 저장소 분석 clone에 사용하는 주소이며 기본값 `http://ops-git-egress-proxy:3128`을 그대로 사용할 수 있다. `OPS_GIT_REMOTE_EGRESS_PROXY_URL`은 사용자 VM 안에서 실행되는 Git이 도달할 수 있는 별도 LAN proxy가 있을 때만 설정한다. 없으면 비워 두며, Docker 내부 서비스명 주소를 넣으면 사용자 VM에서는 해석되지 않는다.

GitHub App ID는 숫자이고 OAuth Client ID는 보통 `Iv1...` 형식인 서로 다른 값이다. `GITHUB_APP_PRIVATE_KEY`에는 GitHub App에서 발급한 PEM 전체를 `\n` 문자로 연결해 넣고, OAuth client secret과 webhook secret도 각각 Dashboard 값으로 설정한다. installation token 404가 발생하면 App ID와 private key가 해당 installation의 App과 일치하는지 먼저 확인한다. `PORTAL_ORIGIN`은 브라우저가 실제로 접속하는 `https://portal.gamjabox.cloud` 같은 origin만 넣고 path는 포함하지 않는다.

Blueprint 검색은 기본 Registry fallback이므로 Elasticsearch가 없으면 `BLUEPRINT_SEARCH_ENABLED=false`를 유지한다. 사용할 때만 연결 URL과 인증 정보를 설정하고, 시작 시 재색인은 관리된 단일 인스턴스에서만 활성화한다. 전체 값 예시는 루트 `env.example`을 기준으로 하며 모든 `CHANGE_ME_*`를 배포 전에 교체한다.

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

- SSH 명령 시간 초과가 원격 프로세스 실패를 뜻하지는 않는다. 긴 apt/dpkg 작업은 단계별 제한과 상태 확인을 사용한다.
- Docker 설치는 dpkg 잠금과 cloud-init 때문에 수 분 걸릴 수 있으며 HTTP 요청 스레드를 점유하지 않는다.
- DB check constraint에 enum 값을 추가할 때 Java enum과 `schema.sql` 제약을 반드시 함께 갱신한다.
- DB 백업 생성은 `DEPLOY`, 이력·검증·다운로드는 `BACKUP_READ` 권한을 사용한다. `gamjabox/backups` 경로는 파일 브라우저와 일반 스트리밍 티켓에서 완전히 차단한다.
- DB 비밀번호는 SSH 명령줄이 아닌 0600 임시 credential 파일로만 전달한다. 덤프 stdout은 평문 파일 없이 즉시 AES-256-GCM으로 암호화하고 SHA-256를 기록하며, 생성 직후와 전용 검증 API에서 전체 복호화를 수행한다.
- 성공한 백업은 VM별 보관 기간과 최대 개수를 적용하고, 실패·기간 초과·개수 초과 파일은 정리한다. 복구 리허설은 [DB 백업 복구 런북](docs/DB_BACKUP_RECOVERY.md)을 따른다.
- 기존 평문 백업은 전용 API에서 다운로드하지 않으며 새 백업 생성 시 보관 정책에 따라 순차 정리한다. 즉시 일괄 삭제는 수행하지 않는다.
- 분석용 Git clone은 Squid allowlist proxy를 기본 사용하고 내부·메타데이터 대역을 연결 시점에 차단한다. 사용자 VM의 Git은 해당 VM에서 도달 가능한 `OPS_GIT_REMOTE_EGRESS_PROXY_URL`을 지정했을 때만 프록시를 적용한다.
- SVG·HTML·XML은 Ops API origin에서 inline 미리보기하지 않고, 다운로드 응답은 `attachment` + `nosniff` + sandbox CSP를 적용한다.
- `OPS_KEY_ENCRYPTION_SECRET`, GitHub private key, webhook secret, OpenAI key를 로그나 배포 스펙에 포함하지 않는다.
- Blueprint manifest나 Runtime을 바꾼 뒤에는 포털 검사와 Ops 빌드를 모두 실행한다.
