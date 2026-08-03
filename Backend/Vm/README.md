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
| Ops 내부 API | `/internal/ops/**`, `/internal/automation/**` | 권한 문맥과 배포 라우트 동기화 |

## 데이터와 외부 의존성

- PostgreSQL/R2DBC: VM, 포트, 조직, 협업 데이터
- Redis Reactive: 단기 티켓, 상태·동시성 보조 데이터
- Proxmox API: VM 생성과 리소스·메트릭 제어
- Cloudflare API: DNS, Tunnel ingress, Zero Trust Access
- User 서비스: 사용자 플랜과 SSH 공개키
- Ops 서비스: 관리 키 등록과 SSH 준비 검증

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
| CNAME 정책 | `RESERVED_SUBDOMAINS` | 사용자 지정 서브도메인 금지 목록 |

Cloudflare 자격 증명과 Tunnel·Zone 설정도 `cloudflare.*` 구성에 필요하다. 운영 값은 저장소 밖에서 주입하고 API 토큰 권한을 DNS·Tunnel·Access 관리에 필요한 최소 범위로 제한한다.

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
- Proxmox 인증서 변경 시 컨테이너 truststore 반영 여부를 먼저 확인한다.
- 동일 CNAME과 VMID 할당은 경쟁 조건을 고려해 DB 제약과 재검증을 함께 사용한다.
