# GamjaBox

> 개인 Proxmox 서버 위에 구축한 셀프호스팅 IaaS 서비스.
> VM 생성부터 SSH 접속, 포트 노출, 배포, 팀 협업까지 — AWS EC2 + 간이 PaaS를 직접 만든 인프라 위에서.

**라이브 서비스** → [gamjabox.cloud](https://portal.gamjabox.cloud)

---

## 왜 만들었나

AWS EC2 같은 VM 생성 경험을 개인 서버 환경에서도 구현해보고 싶었다. 단순히 VM을 띄우는 것에 그치지 않고, 접속 도메인 발급·접근 제어·포트 노출까지 매번 수동으로 반복하던 인프라 작업을 자동화하는 것이 목표였다. VM 하나를 생성하면 SSH 접속용 서브도메인, Zero Trust 접근 정책, Cloudflare Tunnel ingress 설정이 모두 자동으로 만들어진다.

이후 "VM을 만든 다음, 그 안에서 뭘 할지"까지 서비스 범위를 넓혔다 — 웹 터미널, 파일 브라우저, Git 저장소 기반 배포, AI 보조 배포 스펙 생성까지 붙여서, VM 하나로 코드 저장소를 열고 바로 서비스를 올릴 수 있는 수준까지 만드는 게 지금의 목표다.

서비스를 Auth · User · VM · Ops 네 개로 나눈 건 변경 주기와 신뢰 경계가 다르기 때문이다. 인증 로직(토큰 발급·갱신·탈취 감지), 사용자/플랜 관리, Proxmox·Cloudflare 연동, VM 내부 작업(SSH 세션·배포·백업)은 각각 독립적으로 배포·확장돼야 하고, 실제로 VM 서비스만 WebFlux + R2DBC 기반 비동기 구조로 구현하는 등 기술적 선택도 서비스마다 다르다.

---

## 주요 기능

| 기능 | 설명 |
|---|---|
| **VM 프로비저닝** | Proxmox 템플릿 클론 → Static IP 할당 → Cloudflare 연동까지 완전 자동화. SSE로 생성 상태 실시간 수신 |
| **SSH 접속** | VM마다 전용 서브도메인 자동 발급. Cloudflare Zero Trust로 이메일 인증 기반 접근 제어 |
| **포트 노출** | HTTP/TCP 포트를 Cloudflare Tunnel로 외부 노출. PUBLIC(누구나) / PRIVATE(이메일 허용 목록) 구분 |
| **플랜 관리** | FREE / PRO 플랜 전환, 디스크 온라인 확장. 플랜 변경은 관리자 승인 후 반영 |
| **협업 (Organization)** | 팀 단위로 VM 공유. 메모·공지·요청 게시판, 역할별 권한(OWNER / ADMIN / MEMBER) |
| **실시간 메트릭** | CPU·메모리·네트워크·디스크 사용량을 Proxmox API로 수집, SSE 스트림으로 라이브 시각화 |
| **웹 SSH 콘솔** | 브라우저에서 바로 VM 터미널 접속(WebSocket + xterm.js). 로그인 세션과 분리된 일회용 티켓으로 인증 |
| **파일 브라우저** | VM 내부 파일 조회·업로드·다운로드·편집·삭제. 텍스트 편집, 이미지/오디오/비디오 미리보기(Range 스트리밍) 지원 |
| **배포 파이프라인** | Git 저장소 → 이미지 빌드 → 헬스체크 → 실패 시 자동 롤백. 실행 이력 기반 재시도/재배포/수동 롤백, SSE로 실시간 로그 |
| **AI 배포 스펙 생성** | 저장소를 결정론적으로 분석해 확신 가능한 경우(정적 사이트 등)는 AI 호출 없이 규칙 기반으로 확정, 애매한 경우만 구조화 출력으로 AI에 위임. 렌더링된 compose를 AI가 비차단으로 검수 |
| **Docker 관리** | VM 내부 컨테이너/이미지/네트워크/compose 스택 조회 및 제어 |
| **DB 백업** | PostgreSQL/MySQL/MongoDB/Redis 온디맨드 덤프, 파일 브라우저로 다운로드 |

---

## 아키텍처

```
사용자 (포털 프론트, Next.js)
    │
    ▼  API 서버  [Caddy 역프록시 — CORS 포함 일괄 처리]
    │
    ├── Auth 서비스      — 회원가입/로그인/JWT 발급/Refresh Token 로테이션/서비스 간 인증
    ├── User 서비스      — 프로필/SSH 키 관리/플랜 변경 요청
    ├── VM 서비스        — VM CRUD/전원제어/조직 관리/협업
    │       │
    │       ├── Proxmox API          (VM 생성·삭제·전원·리소스 변경·메트릭)
    │       └── Cloudflare API       (CNAME·Tunnel ingress·Zero Trust Access)
    │
    └── Ops 서비스       — 웹 SSH 콘솔/파일 브라우저/Docker 관리/배포 파이프라인/DB 백업
            │
            ├── VM 내부 SSH(JSch)     (git 체크아웃·이미지 빌드·compose 기동·헬스체크·롤백)
            └── OpenAI API            (배포 스펙 AI 자동생성/검수 — 결정론적 분석이 실패한 경우에 한해)
```

서비스 간 호출은 사용자 토큰을 그대로 넘기지 않고, 각 서비스가 자체 client-credentials로 발급받은 audience-scoped 토큰을 사용한다(예: VM → Ops 내부 API).

사용자 포털 외에, 플랜 변경 승인 등을 처리하는 별도 관리자 콘솔이 비공개 도메인으로 분리 운영된다.

**데이터베이스**

```
Auth → MySQL       (Spring MVC + JPA)
User → MySQL       (Spring MVC + JPA)
VM   → PostgreSQL  (Spring WebFlux + R2DBC)
Ops  → PostgreSQL  (Spring MVC + JPA)
```

**캐시 / 상태**

```
Redis — Refresh Token, 이메일 인증 코드, Token Exchange 캐시, 로그인 레이트 리밋 (Auth)
Redis — 웹 콘솔/미디어 스트리밍 티켓, 배포 동시 실행 락, AI 생성 결과 캐시 (Ops)
```

---

## 기술 스택

**Backend**

- Java 17, Spring Boot 4.1 / Spring Security 7.1
- Spring WebFlux + R2DBC (VM 서비스 — 비동기 전체)
- Spring MVC + JPA (Auth/User/Ops 서비스)
- Nimbus JOSE+JWT (RS256), client-credentials 기반 서비스 간 인증
- MySQL, PostgreSQL, Redis
- JSch (Ops — VM 내부 SSH/SFTP 자동화), OpenAI Java SDK(structured output) — 배포 스펙 AI 자동생성/검수

**Frontend**

- Next.js 16 (App Router), TypeScript
- Tailwind CSS
- SSE (VM 상태·메트릭·배포 진행 로그 실시간 수신), WebSocket (웹 SSH 콘솔)

**Infrastructure**

- Proxmox VE (온프레미스 하이퍼바이저)
- Cloudflare Tunnel + Zero Trust Access
- Caddy (리버스 프록시, HTTPS 자동, CORS 일괄 처리)
- Docker Compose (서비스 오케스트레이션)
- GitHub Actions self-hosted runner (서비스별 경로 변경 감지 후 자동 배포)

---

## 인증 흐름

```
1. 로그인 → Access Token (JWT, 15분) + Refresh Token (httpOnly 쿠키, 7일)
2. Access Token 만료 → /auth/refresh → Refresh Token Rotation (새 쌍 발급)
3. 서비스 간 호출 → Token Exchange 또는 client-credentials → audience-scoped 단기 토큰 (Redis 캐시)
4. 토큰 탈취 감지 → 이미 사용된 Refresh Token 재사용 시 해당 유저 전체 세션 강제 만료
```

- `rememberMe` 옵션: ON → 30일 슬라이딩 갱신 / OFF → 고정 7일
- 로그인 레이트 리밋: 이메일 5회 / IP 20회 초과 시 15분 잠금
- 프론트엔드는 Access Token을 메모리(React state)에만 보관, localStorage 미사용

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

실패 시 FAILED로 전환되며 원인이 기록된다.

---

## 배포 파이프라인 (Ops)

VM을 만든 뒤 그 안에 실제 서비스를 올리는 영역. 두 가지 경로를 지원한다.

- **Raw Compose 배포** — 사용자가 직접 작성한 docker-compose 스펙(환경변수, 라우트, 헬스체크 포함)을 그대로 배포
- **AI 보조 배포** — 저장소 URL만 주면 스펙을 자동 생성

AI 보조 배포는 매 요청마다 무조건 AI를 호출하지 않는다:

1. Ops 컨테이너가 대상 저장소를 로컬에 얕게 클론해 `package.json`/`pom.xml`/`build.gradle`/`requirements.txt` 등 매니페스트를 결정론적으로 분석
2. 백엔드 런타임 매니페스트가 전혀 없는 순수 정적 사이트처럼 확신 가능한 경우는 **AI 호출 없이** 규칙 기반으로 스펙 확정
3. 애매한 서비스만 골라 AI에 위임 — 이때도 자유 텍스트가 아니라 구조화 출력(스키마 강제)으로 응답을 받아 파싱 실패 자체를 줄임
4. 빌드/실행 명령은 허용된 전략(enum) 목록에서만 선택되고, 각 전략은 고정된 argv로만 Dockerfile에 반영됨 — AI나 사용자 입력이 임의 셸 명령으로 이어지는 경로 자체가 없음
5. 근거가 부족하면 스펙을 지어내는 대신 `NEEDS_INPUT`/`UNSUPPORTED`/`CONFLICT` 같은 명시적 상태로 응답
6. 최종 렌더링된 compose 원문을 AI가 한 번 더 비차단으로 검수(운영상 위험·누락 헬스체크 등 코멘트만 제공, 배포를 막지 않음). 전송 전 시크릿처럼 보이는 값은 마스킹

배포 실행 중에는 이미지 빌드·태깅, DB 기반 SSE 실시간 로그(재접속 시 이벤트 재생), 헬스체크 실패 시 자동 롤백이 이뤄진다. 실패한 배포는 같은 스펙으로 재시도하거나 값을 수정 후 재배포할 수 있고, 과거 성공한 배포로 수동 롤백도 가능하다(재빌드 없이 해당 시점 이미지로 컨테이너만 재기동).

---

## 도메인 구조

| 서브도메인 | 용도 |
|---|---|
| `portal.*` | 사용자 포털 (Next.js) |
| `api.*` | 백엔드 API |
| `{vm-prefix}-{shortId}.*` | 사용자 VM SSH 접속 (VM 생성 시 자동 발급) |
| `{vm-prefix}-{shortId}-{portNickname}.*` | VM 추가 노출 포트 |

관리자 콘솔은 별도 비공개 도메인으로 분리되어 있으며 여기서는 다루지 않는다.

---

## 프로젝트 구조

```
GJ-Cloud/
├── Backend/
│   ├── Auth/    Spring MVC — 인증·JWT·Refresh Token·이메일 인증
│   ├── User/    Spring MVC — 프로필·SSH 키·플랜
│   ├── vm/      Spring WebFlux — VM·포트·조직·협업·메트릭
│   └── Ops/     Spring MVC — 웹 SSH 콘솔·파일 브라우저·배포 파이프라인·AI 스펙 생성·DB 백업
└── Frontend/
    └── portal/  Next.js — 사용자 포털 + 관리자 콘솔(같은 앱, 도메인으로 분리)
```

---

## 실기(e2e) 검증에서 발견해 고친 문제들

로컬 환경만으로는 재현되지 않던 실제 VM 대상 이슈들:

- VM cloud-init에 DNS 서버가 설정되지 않아, 게이트웨이가 DNS를 포워딩하지 않으면 VM 내부 `git clone`/`curl` 등 도메인 조회가 전부 실패하던 문제
- Docker 설치 성공 여부를 `curl | sh` 파이프의 종료 코드로만 판단해, curl이 네트워크 오류로 실패해도 성공으로 오판하던 문제
- Docker는 설치되지만 접속 계정을 `docker` 그룹에 자동으로 넣어주지 않아 이후 모든 docker 명령이 권한 오류로 실패하던 문제
- 갓 생성된 VM에서 cloud-init이 부팅 직후 자체 `apt-get`을 실행 중이라 dpkg 락 경합으로 Docker 설치가 실패하던 문제
- 배포 SSE 스트림 종료 시 Spring Security가 컨테이너의 ASYNC 재디스패치에서 인증 컨텍스트를 못 찾아 인가 거부 → 이미 커밋된 SSE 응답이 깨지던 문제
- 서비스 간 인증 설정 바인딩 오류로 VM→Auth 서비스 토큰 발급이 전부 401로 실패해 VM 생성이 막히던 문제
- AI 기반 배포 스펙 생성이 정적 사이트를 Node.js로 오분류(존재하지 않는 포트·헬스체크를 지어냄)하던 문제 → 결정론적 저장소 분석 도입으로 해결

VM 생성부터 Docker 설치, AI 자동생성 기반 배포까지 실제 VM 대상 end-to-end 검증 완료.

---

## 제약사항 및 설계 결정

- **디스크 축소 불가** — Proxmox/QEMU 자체 제약. 확장만 가능
- **플랜 변경 후 재부팅 필요** — cores/memory 변경은 재부팅 전까지 미적용 (`needsReboot` 필드로 UI 안내)
- **Static IP** — VM IP는 재부팅해도 변경되지 않음. DHCP 범위와 충돌하지 않는 별도 풀 사용
- **CORS는 Caddy 담당** — 프로덕션에서 Spring 서버단 CORS 설정 없음. Caddy가 일괄 처리
- **소셜 로그인 미구현** — MVP 범위 외
- **VM 슬롯 제한** — 플랜별로 제한된 대수만 생성 가능 (물리 서버 IP 풀 기준)
- **포트 최대 5개, 접근 이메일 최대 10개** — VM당 제한
- **Git 저장소 URL/브랜치/PAT는 저장하지 않음** — 재시도·재배포 시 재입력 필요(보안상 의도된 제약)
- **자동/정기 DB 백업 미구현** — 현재는 온디맨드 수동 백업만 지원

---

<p align="center">
  <sub>Built with ☕ on a home server</sub>
</p>
