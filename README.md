# GamjaBox

> 개인 Proxmox 서버 위에 구축한 셀프호스팅 IaaS 서비스.  
> VM 생성부터 SSH 접속, 포트 노출, 팀 협업까지 — AWS EC2와 유사한 경험을 직접 만든 인프라 위에서.

**라이브 서비스** → [gamjabox.cloud](https://portal.gamjabox.cloud)

---

## 주요 기능

| 기능 | 설명 |
|---|---|
| **VM 프로비저닝** | Proxmox 템플릿 클론 → IP 할당 → Cloudflare 연동까지 완전 자동화. SSE로 생성 상태 실시간 수신 |
| **SSH 접속** | VM마다 전용 서브도메인 자동 발급. Cloudflare Zero Trust로 이메일 인증 기반 접근 제어 |
| **포트 노출** | HTTP/TCP 포트를 Cloudflare Tunnel로 외부 노출. PUBLIC(누구나) / PRIVATE(이메일 허용 목록) 구분 |
| **플랜 관리** | FREE / PRO 플랜 전환, 디스크 온라인 확장. 플랜 변경은 관리자 승인 후 반영 |
| **협업 (Organization)** | 팀 단위로 VM 공유. 메모·공지·요청 게시판, 역할별 권한(OWNER / ADMIN / MEMBER) |
| **실시간 메트릭** | CPU·메모리·네트워크·디스크 사용량을 SSE 스트림으로 라이브 시각화 |

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
            ├── Proxmox API          (VM 생성·삭제·전원·리소스 변경)
            └── Cloudflare API       (CNAME·Tunnel ingress·Zero Trust Access)
```

**데이터베이스**

```
Auth → MySQL   (Spring MVC + JPA)
User → MySQL   (Spring MVC + JPA)
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
│   └── vm/      Spring WebFlux — VM·포트·조직·협업·메트릭
└── Frontend/
    └── portal/  Next.js — 사용자 포털
```

---

## 제약사항 및 설계 결정

- **디스크 축소 불가** — Proxmox/QEMU 자체 제약. 확장만 가능
- **플랜 변경 후 재부팅 필요** — cores/memory 변경은 재부팅 전까지 미적용 (`needsReboot` 필드로 UI 안내)
- **Static IP** — VM IP는 재부팅해도 변경되지 않음. DHCP 범위와 충돌하지 않는 별도 풀 사용
- **CORS는 Caddy 담당** — 프로덕션에서 Spring 서버단 CORS 설정 없음. Caddy가 일괄 처리
- **소셜 로그인 미구현** — MVP 범위 외
- **VM 슬롯 제한** — FREE 5대 / PRO 3대 (물리 서버 IP 풀 기준)
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
