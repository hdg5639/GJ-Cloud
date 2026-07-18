# GamjaBox

> 개인 Proxmox 서버 위에 구축한 셀프호스팅 IaaS 서비스.  
> VM 생성부터 SSH 접속, 포트 노출, 팀 협업까지 — AWS EC2와 유사한 경험을 직접 만든 인프라 위에서.

**라이브 서비스** → [gamjabox.cloud](https://portal.gamjabox.cloud)

---

## 왜 만들었나

AWS EC2 같은 VM 생성 경험을 개인 서버 환경에서도 구현해보고 싶었다. 단순히 VM을 띄우는 것에 그치지 않고, 접속 도메인 발급·접근 제어·포트 노출까지 매번 수동으로 반복하던 인프라 작업을 자동화하는 것이 목표였다. 결과적으로 VM 하나를 생성하면 SSH 접속용 서브도메인, Zero Trust 접근 정책, Cloudflare Tunnel ingress 설정이 모두 자동으로 만들어진다.

서비스를 Auth · User · VM 세 개로 나눈 건 변경 주기가 다르기 때문이다. 인증 로직(토큰 발급·갱신·탈취 감지)과 VM 제어 로직(Proxmox/Cloudflare 연동)은 독립적으로 배포·변경되어야 하고, 실제로도 VM 서비스만 WebFlux 기반 비동기 구조로 구현하는 등 기술적 선택지가 달라졌다.

---

## 주요 기능

| 기능 | 설명 |
|---|---|
| **VM 프로비저닝** | Proxmox 템플릿 클론 → IP 할당 → Cloudflare 연동까지 완전 자동화. SSE로 생성 상태 실시간 수신 |
| **SSH 접속** | VM마다 전용 서브도메인 자동 발급. Cloudflare Zero Trust로 이메일 인증 기반 접근 제어 |
| **포트 노출** | HTTP/TCP 포트를 Cloudflare Tunnel로 외부 노출. PUBLIC(누구나) / PRIVATE(이메일 허용 목록) 구분 |
| **플랜 관리** | FREE / PRO 플랜 전환, 디스크 온라인 확장. 플랜 변경은 관리자 승인 후 반영 |
| **협업 (Organization)** | 팀 단위로 VM 공유. 메모·공지·요청 게시판, 역할별 권한(OWNER / ADMIN / MEMBER) |
| **실시간 메트릭** | CPU·메모리·네트워크·디스크 사용량을 Proxmox API로 수집, SSE 스트림으로 라이브 시각화. 디스크 used는 QEMU Guest Agent로 별도 조회 |

---

## 아키텍처

```
사용자 (포털 프론트)
    │
    ▼  API 서버  [Caddy 역프록시]
    │
    ├── Auth 서비스      — 회원가입/로그인/JWT 발급/Refresh Token 로테이션
    ├── User 서비스      — 프로필/SSH 키 관리/플랜 변경 요청
    └── VM 서비스        — VM CRUD/전원제어/Cloudflare 연동/조직 관리/협업
            │
            ├── Proxmox API          (VM 생성·삭제·전원·리소스 변경·메트릭)
            └── Cloudflare API       (CNAME·Tunnel ingress·Zero Trust Access)
```

**데이터베이스**

```
Auth → MySQL       (Spring MVC + JPA)
User → MySQL       (Spring MVC + JPA)
VM   → PostgreSQL  (Spring WebFlux + R2DBC)
```

**캐시 / 상태**

```
Redis — Refresh Token, 이메일 인증 코드, Token Exchange 캐시, 로그인 레이트 리밋
```

---

## 기술 스택

**Backend**

- Java 17, Spring Boot 3
- Spring WebFlux + R2DBC (VM 서비스 — 비동기 전체)
- Spring MVC + JPA (Auth/User 서비스)
- Spring Security, Nimbus JOSE+JWT (RS256)
- MySQL, PostgreSQL, Redis

**Frontend**

- Next.js 15 (App Router), TypeScript
- Tailwind CSS
- SSE (VM 상태 실시간 수신, 메트릭 스트림)

**Infrastructure**

- Proxmox VE (온프레미스 하이퍼바이저)
- Cloudflare Tunnel + Zero Trust Access
- Caddy (리버스 프록시, HTTPS 자동)
- Docker Compose (서비스 오케스트레이션)

---

## 인증 흐름

```
1. 로그인 → Access Token (JWT, 15분) + Refresh Token (httpOnly 쿠키, 7일)
2. Access Token 만료 → /auth/refresh → Refresh Token Rotation (새 쌍 발급)
3. 서비스 간 호출 → Token Exchange → audience-scoped 단기 토큰 (Redis 캐시 15분)
4. 토큰 탈취 감지 → 이미 사용된 Refresh Token 재사용 시 해당 유저 전체 세션 강제 만료
```

- `rememberMe` 옵션: ON → 30일 슬라이딩 갱신 / OFF → 고정 7일
- 로그인 레이트 리밋: 이메일 5회 / IP 20회 초과 시 15분 잠금

---

## VM 프로비저닝 흐름

```
POST /vms  →  PENDING (즉시 202 응답)
    │
    ▼  백그라운드 비동기 파이프라인
    │
    CREATING  →  SSH 공개키 조회 + Static IP 할당
    BOOTING   →  Proxmox 템플릿 클론 → vmid 배정 → 디스크 리사이즈 → VM 시작
    RUNNING   →  Guest Agent로 IP 확인
              →  Cloudflare: CNAME 등록 → Tunnel ingress 추가 → Zero Trust Access 생성
    │
    ▼  SSE (/vms/events/subscribe) 로 클라이언트 실시간 수신
```

---

## 도메인 구조

| 서브도메인 | 용도 |
|---|---|
| `portal.*` | 사용자 포털 (Next.js) |
| `api.*` | 백엔드 API |
| `{vm-prefix}-{shortId}.*` | 사용자 VM SSH 접속 (VM 생성 시 자동 발급) |
| `{vm-prefix}-{shortId}-{portNickname}.*` | VM 추가 노출 포트 |

---

## 프로젝트 구조

```
GJ-Cloud/
├── Backend/
│   ├── Auth/    Spring MVC — 인증·JWT·Refresh Token·이메일 인증
│   ├── User/    Spring MVC — 프로필·SSH 키·플랜
│   ├── vm/      Spring WebFlux — VM·포트·조직·협업·메트릭
│   └── Ops/     Spring MVC — 웹 SSH 콘솔·파일 브라우저·배포 파이프라인 (신규)
└── Frontend/
    └── portal/  Next.js — 사용자 포털
```

---

## 진행 상황 — gamjabox Ops 확장 (웹 콘솔 · 파일 브라우저 · 배포 파이프라인)

VM을 만든 뒤 "그 안에서 뭔가 하는" 영역(터미널 접속, 파일 관리, 배포)을 전담하는 신규 Ops 서비스를 붙이는 작업 진행 중.

### 완료된 작업

- **Ops 서비스 신설** — Spring MVC + PostgreSQL + Redis, VM 서비스와 내부 API로 연동 (JWT audience 분리)
- **웹 SSH 콘솔 (백엔드)** — WebSocket + JSch, 일회용 티켓 인증(Redis GETDEL), VM별 Ed25519 관리 키 자동 발급·암호화 저장
- **파일 브라우저 (백엔드)** — SFTP 기반 조회·업로드·다운로드·편집·삭제, 심볼릭 링크 탈출 방지(SFTP REALPATH), 바이너리 파일 편집 차단
- **배포 파이프라인** — bare repo + git worktree 기반 소스 체크아웃, Raw Compose(D-2) 및 기본 템플릿(D-1: Spring/Next.js/React/Node/Python/NestJS) 배포, 이미지 빌드·태깅, DB 기반 SSE 실시간 로그(재접속 시 이벤트 재생), 헬스체크 실패 시 자동 롤백
- **VM 서비스 연동** — 배포가 만든 라우트를 VM 서비스의 기존 Cloudflare/포트 로직과 동기화(수동 등록 포트는 건드리지 않음)
- **Docker 관리 (백엔드)** — 컨테이너/이미지/네트워크/compose 스택 조회 및 제어, 조회(Member 허용)와 제어(Owner/Admin 전용) 권한 분리
- **AI 기반 배포 스펙 자동 생성 (백엔드, D-3)** — 서비스 카드(스택/디렉토리/포트) + 공유 인프라 선택을 OpenAI API(gpt-5.6-terra/sol, 복잡도·재교정에 따라 모델·reasoning effort 티어링)로 완전한 DeploymentSpec JSON으로 변환, 기존 Validator로 검증(실패 시 1~2회 자동 재교정) 후 검토용으로 반환(즉시 배포되지 않음)
- **AI 기반 compose 검수 (백엔드, D.5-1)** — 결정론적 검증을 이미 통과한 스펙에 한해 비차단 AI 검수 요청, 코멘트만 반환하고 배포를 승인/거부하지 않음(실패해도 배포에 영향 없음)
- **수동 DB 백업 (백엔드)** — postgresql/mysql/mongodb/redis 온디맨드 덤프, VM의 backups/ 디렉토리에 저장 후 기존 파일 브라우저로 다운로드(자동/정기 백업은 범위 밖)
- **웹 SSH 콘솔 (프론트엔드)** — `@xterm/xterm` + WebSocket, 인스턴스 상세 페이지에서 별도 라우트(`/instances/{id}/console`)로 진입, 일회용 티켓 발급 → WS 핸드셰이크 → 리사이즈 동기화까지 연동
- **파일 브라우저 (프론트엔드)** — 디렉토리 탐색/브레드크럼/`..` 상위 이동, 업로드·새 폴더·삭제, 텍스트 파일 편집(바이너리/용량 초과 시 편집 대신 다운로드 안내), 이미지/오디오/비디오 미리보기(HTTP Range 지원 스트리밍 — 짧은 TTL 재사용 티켓으로 인증, seek/버퍼링 가능)
- **Docker 관리 (프론트엔드)** — 설치 확인/설치, 컨테이너 제어(시작/정지/재시작/삭제/로그), 이미지/네트워크/Compose 스택 조회, 네트워크 생성/삭제
- **배포 파이프라인 (프론트엔드)** — Raw Compose 배포(환경변수·라우트·헬스체크 고급설정), AI 스펙 자동생성+검수 연동, 배포 이력 조회, 배포 상세 페이지의 SSE 실시간 진행 로그(EventSource의 커스텀 헤더 제약을 fetch 스트리밍 직접 파싱 + afterSequence 기반 재연결로 해결)
- **DB 백업 (프론트엔드)** — 수동 백업 트리거 + 이력 조회 + 파일 브라우저 재사용 다운로드
- **서비스 간 인증 강화 (OPS-SEC-002)** — VM→Ops 내부 API(`/internal/vms/{vmId}/management-key`) 호출을 로그인 사용자의 토큰을 그대로 전달하는 방식에서 client-credentials 기반 서비스 신원 인증으로 전환. 기존에는 임의의 로그인 사용자가 `/auth/token/exchange`로 `vm-service` 오디언스 토큰을 스스로 발급받아 Ops의 관리 키 발급/폐기 API를 직접 호출할 수 있는 권한 상승 취약점이 있었음. Auth에 서비스 전용 토큰 발급 엔드포인트(`/auth/token/service`, client_id/secret 기반)를 추가하고 VM 서비스가 여기서 받은 자체 서비스 토큰을 캐싱해 사용하도록 변경, Ops의 `InternalJwtValidator`는 `token_type=service` + `client_id` 클레임까지 검증하도록 강화
- **Ops 서버 보안 강화 (OPS-SEC-001, 003~006)** — 배포 조회/이벤트 엔드포인트 권한 체크, compose 서비스명·git 브랜치명 허용목록 검증, 관리 키 상태 재사용 차단, 스트리밍 티켓 요청 시점 재검증, HTTP Range 파서 버그 수정

### 진행 중인 작업

- 실제 VM 대상 종단간(end-to-end) 동작 검증 — 로컬 환경 제약으로 코드 레벨 검증까지만 완료, 실기 배포 테스트 필요

---

## 제약사항 및 설계 결정

- **디스크 축소 불가** — Proxmox/QEMU 자체 제약. 확장만 가능
- **플랜 변경 후 재부팅 필요** — cores/memory 변경은 재부팅 전까지 미적용 (`needsReboot` 필드로 UI 안내)
- **Static IP** — VM IP는 재부팅해도 변경되지 않음. DHCP 범위와 충돌하지 않는 별도 풀 사용
- **CORS는 Caddy 담당** — 프로덕션에서 Spring 서버단 CORS 설정 없음. Caddy가 일괄 처리
- **소셜 로그인 미구현** — MVP 범위 외
- **VM 슬롯 제한** — FREE 3대 / PRO 3대 (물리 서버 IP 풀 기준)
- **포트 최대 5개, 접근 이메일 최대 10개** — VM당 제한

---

## 스크린샷

| 인스턴스 목록 | 인스턴스 상세 |
|---|---|
| ![instances](docs/screenshots/instances.png) | ![instance-detail](docs/screenshots/instance-detail.png) |

| 조직 협업 | 실시간 메트릭 |
|---|---|
| ![org](docs/screenshots/org.png) | ![metrics](docs/screenshots/metrics.png) |

---

<p align="center">
  <sub>Built with ☕ on a home server</sub>
</p>
