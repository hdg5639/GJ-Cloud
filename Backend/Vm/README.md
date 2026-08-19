# VM Service

GamjaBox의 VM 수명주기, Proxmox 프로비저닝, Cloudflare 외부 노출, 조직·협업과 실시간 메트릭을 담당하는 Spring WebFlux 서비스다.

> 전체 시스템 구성은 [프로젝트 루트 README](../../README.md)를 참고한다.

## 책임 범위

- VM 생성·조회·삭제와 전원 제어
- Proxmox 템플릿 클론, 디스크 확장, Guest Agent 기반 IP 확인
- 사용자 키와 Ops 관리 키 주입·검증·복구
- HTTP/TCP 포트와 Cloudflare Tunnel·CNAME·Access 정책 관리
- 조직, 멤버 역할, VM 공유, 협업 메모·공지·요청
- Proxmox 메트릭 조회와 SSE 실시간 전달
- Ops 배포 라우트와 수동 포트 연결을 위한 내부 API 제공
- `AUTO_PREVIEW` 시스템 워커의 제한된 Proxmox 프로비저닝·전원·Preview 라우트 내부 API

VM 내부 SSH 명령, Docker, 파일, 배포 실행은 Ops가 담당한다. VM 서비스는 VM의 상태와 접근 권한, 외부 라우팅의 정본을 소유한다.

## 프로비저닝 흐름

```text
POST /vms
  → PENDING / 202 응답
  → CREATING: 사용자 SSH 키 + Proxmox VMID 확보
  → BOOTING: Proxmox 클론, cloud-init DHCP·DNS, 디스크, VM 시작
  → Guest Agent IP 확인
  → Ops 관리 키 SSH 준비 + 사용자 authorized_keys 검증·복구
  → Cloudflare CNAME/Tunnel/Access 구성
  → RUNNING 이벤트 발행
```

오래 걸리는 작업은 요청 스레드에서 끝까지 기다리지 않는다. 상태 변화는 `/vms/events/subscribe` SSE로 전달하며, 스트림 연결 전 단기 티켓을 발급한다.

cloud-init 네트워크는 `ipconfig0=ip=dhcp`로 설정하여 개발·운영이 공유하는 LAN에서 각자 다른 DB 내부 IP 풀을 고르는 문제를 피한다. DHCP가 배정한 실제 IPv4는 Guest Agent로 확인하여 `internal_ip`에 저장한다.

## API 영역

| 영역 | 대표 경로 | 설명 |
|---|---|---|
| VM | `/vms`, `/vms/{vmId}` | 생성·조회·삭제 |
| 전원·플랜 | `/vms/{vmId}/power`, `/vms/{vmId}/plan` | 전원 제어와 리소스 변경 |
| SSH Access | `/vms/{vmId}/ssh-access` | Zero Trust 접근 이메일 관리 |
| 포트 | `/vms/{vmId}/ports/**` | 외부 노출과 포트별 Access 정책 |
| 메트릭 | `/vms/{vmId}/metrics/**` | 현재·이력·SSE 스트림 |
| 조직 | `/vms/organizations/**` | 조직·초대·멤버 역할·VM 공유 |
| 협업 | `/vms/collaborations/**`, `/vms/collaboration-tags/**` | 메모·공지·요청과 태그 |
| 관리자 | `/admin/vms/**` | 전체 VM 조회와 강제 삭제 |
| Ops 내부 API | `/internal/ops/**`, `/internal/automation/**` | 권한 문맥, 최대 500개 VM 존재 여부 일괄 조회, 배포 라우트 동기화 |
| 시스템 워커 | `/internal/automation/system-workers/auto-preview/**` | 고정 사양 프로비저닝·전원·관리형 Preview CNAME/Tunnel |

ControlBox는 `/admin/vms/page?page=1&size=50`로 삭제되지 않은 VM을 DB 페이징한다. 기존 `/admin/vms` 전체 목록은 호환성을 위해 유지하지만 신규 화면에서 사용하지 않는다. 활성 VM 수와 관리자 최근순, 협업 항목의 pinned+최근순, 태그의 사용량+최근순 조회는 해당 필터·정렬에 맞춘 복합/partial 인덱스를 사용한다. 대부분의 행을 반환하는 활성 Proxmox VMID 조회는 커버링 인덱스보다 sequential scan이 싸다는 실측 결과에 따라 불필요한 인덱스를 두지 않는다.

Ops의 고아 배포 조정은 `/internal/automation/vms/existence`에 UUID를 최대 500개씩 전달해 삭제되지 않은 VM ID만 한 번에 확인한다. VM 서비스가 다른 내부 서비스를 호출할 때 사용하는 client-credentials 토큰은 만료 전 안전 여유를 두고 client ID별로 캐시하며, 동시 갱신 요청은 하나의 발급 요청을 공유한다.

## 데이터와 외부 의존성

- PostgreSQL/R2DBC: VM, 포트, 조직, 협업 데이터
- Redis Reactive: 단기 티켓, 상태·동시성 보조 데이터
- Proxmox API: VM 생성과 리소스·메트릭 제어
- Cloudflare API: DNS, Tunnel ingress, Zero Trust Access
- User 서비스: 사용자 플랜과 SSH 공개키
- Ops 서비스: 관리 키 등록과 SSH 준비 검증

Cloudflare의 멱등한 DNS·Tunnel·Access 조회·갱신·삭제 호출은 네트워크 오류, 429, 5xx에 최대 4회 지수 backoff를 적용한다. CNAME 생성은 중복 응답을 재조회할 수 있어 같은 정책을 적용하지만, Access App·Policy 생성 POST는 응답 유실 시 중복 리소스가 생길 수 있어 무조건 재시도하지 않는다. CNAME 생성이 중복으로 거절되면 같은 FQDN이면서 현재 Tunnel을 가리키는 레코드만 재사용하고, 다른 대상의 레코드는 충돌로 처리한다. Tunnel ingress는 같은 hostname 규칙을 교체하는 멱등 동작으로 갱신한다.

포트 프로비저닝은 `CNAME → ingress → Access App/Policy → DB` 순으로 진행하며 중간 실패 시 이번 시도에서 생성한 리소스만 역순으로 정리한다. 기존 CNAME을 안전하게 채택한 경우에는 후속 실패가 나도 해당 DNS 레코드를 삭제하지 않는다. 배포 대상에 연결된 수동 CNAME이 포트·프로토콜·공개 범위·subdomain까지 요청 라우트와 일치하면 새 레코드를 만들지 않고 그 라우트를 사용한다.

## 권한 모델

- 개인 VM은 소유자 문맥으로 접근한다.
- 조직 공유 VM은 `OWNER`, `ADMIN`, `MEMBER` 역할과 작업별 권한을 조합한다.
- DB 백업 이력·검증·다운로드는 `BACKUP_READ`로 분리하며 `MEMBER`에게는 부여하지 않는다.
- Ops 내부 경로는 일반 사용자 JWT가 아니라 지정된 audience/scope의 서비스 토큰만 허용한다.
- SSE 스트림은 URL에 장기 Access Token을 노출하지 않고 일회성 티켓으로 인증한다.

## 환경변수

| 분류 | 변수 | 용도 |
|---|---|---|
| DB | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL R2DBC 연결 |
| Redis | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Reactive Redis 연결 |
| 인증 | `AUTH_SERVER_URL`, `VM_SERVICE_CLIENT_SECRET` | JWKS·서비스 인증 |
| 서비스 연동 | `USER_SERVICE_URL`, `OPS_SERVICE_URL` | 사용자 키와 SSH 준비·배포 연동 |
| Proxmox | `PROXMOX_URL`, `PROXMOX_TOKEN_ID`, `PROXMOX_TOKEN_SECRET` | API 연결과 인증 |
| Proxmox 배치 | `PROXMOX_NODE`, `PROXMOX_STORAGE`, `PROXMOX_BRIDGE` | 노드·스토리지·브리지 |
| VMID 범위 | `PROXMOX_VMID_START`, `PROXMOX_VMID_END` | 자동 할당 허용 범위 |
| Cloudflare | `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_ZONE_ID`, `CLOUDFLARE_TUNNEL_ID`, `CLOUDFLARE_BASE_DOMAIN` | DNS·Tunnel·Access와 기본 도메인 |
| CNAME 정책 | `RESERVED_SUBDOMAINS` | 사용자 지정 서브도메인 금지 목록 |
| 시스템 워커 | `SYSTEM_WORKER_AUTO_PREVIEW_ENABLED`, `SYSTEM_WORKER_AUTO_PREVIEW_VMID`, `SYSTEM_WORKER_AUTO_PREVIEW_CORES`, `SYSTEM_WORKER_AUTO_PREVIEW_MEMORY_MB`, `SYSTEM_WORKER_AUTO_PREVIEW_DISK_GB`, `SYSTEM_WORKER_AUTO_PREVIEW_TEMPLATE_VMID` | Ops 서비스와 공유하는 고정 워커 계약 |
| API 문서 | `OPENAPI_SERVER_URL` | `docs` 프로파일의 Try it out 대상 API Gateway URL |

Auto Preview Worker 기본값은 VMID 300, 4 vCPU, 5120MB RAM, 80GB disk, template VMID 9026이다. VM 서비스는 요청과 서버 설정이 정확히 일치할 때만 생성하며, 예약 VMID의 VM 이름이 전용 worker 식별자와 다르면 조작을 거부한다. 활성 worker 삭제 API는 없고, 클론이 완료된 최초 생성 실패 보상에서만 생성 중인 VM을 정리한다. 생성 파이프라인은 Guest Agent IP가 준비될 때까지 대기하지만, 운영 상태 조회는 최초 20초 대기·재시도 없이 IP를 한 번만 확인하고 전체 요청을 10초로 제한한다.

`PROXMOX_URL`은 `https://pve.example.internal:8006/api2/json`처럼 API root까지 포함하고, Token ID는 `<user>@<realm>!<token-name>` 형식으로 넣는다. VMID 범위는 Proxmox에서 이미 사용하거나 다른 환경에 예약한 범위와 겹치지 않게 개발·운영별로 분리한다.

Cloudflare의 Account ID, Zone ID, Tunnel UUID는 Dashboard에서 확인하고 API Token 권한은 DNS·Tunnel·Access 관리에 필요한 최소 범위로 제한한다. `RESERVED_SUBDOMAINS`는 `www,portal,admin,api,ssh`처럼 쉼표로 구분한다. DB·Redis 비밀번호와 서비스 secret은 서버별로 별도 생성하며, 전체 형식과 예시는 루트 `env.example`을 기준으로 한다.

배포 환경의 OpenAPI JSON은 `prod,docs` 프로파일에서만 활성화되며 Swagger UI는 Ops 통합 문서에서 제공한다. 운영은 `prod`만 유지한다.

`OpenApiExampleCustomizer`는 공개 비관리자 API의 요청 DTO, path/query/header/cookie 입력과 응답 DTO에 안전한 목업 예시를 자동 보완한다. `ApiResponse<T>` 응답은 실제 `T`를 펼친 완성형 예시로 만들고 `Void`와 성공 메타데이터는 `null` 계약을 반영한다. 명시적 `@Schema(example=...)`는 덮어쓰지 않으며 `/admin/**`, `/internal/**`에는 적용하지 않는다.

## 로컬 실행과 검증

요구사항은 JDK 17, PostgreSQL, Redis다. 실제 프로비저닝 테스트에는 접근 가능한 Proxmox와 Cloudflare 개발 환경이 필요하다.

```bash
cd Backend/Vm
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

```bash
./gradlew test
./gradlew clean build
```

```bash
docker compose config
```

서비스 상태는 `/actuator/health`에서 확인한다. 개발 중 실제 VM 생성이 불필요한 검증은 외부 API client를 격리해 수행한다.

## 배포

- `develop`의 `Backend/Vm/**` 변경은 `deploy-vm.yml`을 통해 개발 VM에 배포된다.
- `main` 변경은 `deploy-main-vm.yml`을 통해 운영 VM에 배포된다.
- VM 서비스는 콜드 스타트 시간을 고려해 최대 100초 동안 컨테이너 내부 IP의 Actuator를 확인한다.
- 헬스체크 실패 시 보존된 직전 이미지로 자동 롤백한다.

## 운영 시 주의점

- DB 상태만 RUNNING으로 바꾸지 말고 실제 관리 키 SSH와 사용자 키 fingerprint까지 확인한다.
- 삭제·실패 보상 단계에서는 만들어진 Proxmox, DNS, Tunnel, Access 리소스를 역순으로 정리한다.
- Ops 배포 라우트 동기화는 빈 목록도 “모든 라우트 제거”라는 유효한 명령이다.
- 연결된 수동 CNAME은 배포 라우트와 정확히 일치할 때 재사용하며, 동기화가 수동 포트를 삭제·수정하지 않는다.
- Proxmox 인증서 변경 시 컨테이너 truststore 반영 여부를 먼저 확인한다.
- 동일 CNAME과 VMID 할당은 경쟁 조건을 고려해 DB 제약과 재검증을 함께 사용한다.
