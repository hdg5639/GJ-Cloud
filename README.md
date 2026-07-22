<p align="center">
  <img src="Frontend/portal/public/gamjabox-wordmark.svg" alt="GamjaBox" width="320">
</p>

<p align="center">
  <img src="Frontend/portal/public/favicon-32x32.png" alt="" width="16" height="16">
  개인 Proxmox 서버 위에 구축한 셀프호스팅 IaaS 서비스.<br>
  VM 생성부터 SSH 접속, 포트 노출, 배포, 팀 협업까지 — AWS EC2 + 간이 PaaS를 직접 만든 인프라 위에서.
</p>

<p align="center"><b>라이브 서비스</b> → <a href="https://gamjabox.cloud">gamjabox.cloud</a></p>

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

사용자 포털 외에, <img src="Frontend/portal/public/controlbox-symbol.svg" alt="ControlBox" width="18" height="18" align="absmiddle"> **ControlBox** — 플랜 변경 승인 등을 처리하는 별도 관리자 콘솔이 비공개 도메인으로 분리 운영된다.

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

## 트러블슈팅

처음부터 지금까지 개발하면서 실제로 겪고 고친 문제들을 영역별로 정리했다. 대부분 로컬 환경에서는 재현되지 않고 실제 배포·실기 테스트 중에 드러난 것들이다.

### 인증 / 보안

- Spring Security(MVC·WebFlux 둘 다)는 인증 실패 시 기본적으로 403을 반환 — API 클라이언트 입장에서 "인증 안 됨(401)"과 "권한 없음(403)"이 구분되지 않던 문제 → `AuthenticationEntryPoint`/`exceptionHandling`을 명시해 401로 통일 (Auth, VM 각각에서 별도로 발견)
- WebFlux에서 `CorsWebFilter`에 순서를 지정하지 않으면 Spring Security 필터 체인보다 늦게 실행돼, 인증 실패(401) 응답에도 CORS 헤더가 안 붙어 브라우저가 진짜 에러 대신 CORS 에러로 표시하던 문제 → `HIGHEST_PRECEDENCE`로 고정. 이후 이런 종류의 문제를 근본적으로 없애기 위해 서버단 CORS 설정 자체를 제거하고 Caddy로 일원화
- `EventSource`(SSE)는 커스텀 헤더를 못 보내 `Authorization` 헤더 기반 인증이 안 먹히는 문제 — VM 이벤트, 메트릭 SSE에서 각각 겪음 → 쿼리 파라미터 토큰 인증 경로를 별도로 추가
- 프론트 Access Token 자동 갱신 로직이 React StrictMode의 이중 마운트로 동시에 두 번 실행되며 Refresh Token Rotation과 충돌(먼저 도착한 새 토큰 쌍이 나중 요청에 의해 무효화됨) → 갱신 중 플래그로 재진입 차단
- 서비스 간 인증 설정(`auth.service-clients`)이 YAML 중첩 레벨 하나가 빠진 채로 작성돼 있어 실제로는 항상 빈 맵으로 바인딩 → VM→Auth 서비스 토큰 발급이 전부 401로 실패해 **VM 생성 자체가 막히던** 문제 (배포 후 실기 테스트에서 발견)
- VM→Ops 내부 API(관리 키 발급/폐기)를 로그인 사용자의 토큰을 그대로 전달하는 방식으로 만들었다가, 임의의 로그인 사용자가 토큰 교환 API로 동일 오디언스 토큰을 스스로 발급받아 이 내부 API를 직접 호출할 수 있는 **권한 상승 취약점**을 자체 점검 중 발견 → client-credentials 기반 서비스 신원 인증(서비스 전용 토큰 발급 엔드포인트 + `token_type=service` 클레임 검증)으로 전환
- 같은 보안 점검에서 한 번에 발견해 수정한 나머지 항목: 배포 조회/이벤트 엔드포인트 권한 체크 누락, compose 서비스명·git 브랜치명을 블랙리스트로만 걸러 셸 인젝션 여지가 남아있던 문제(허용목록 방식으로 전환), 폐기 대기 상태 관리 키의 재사용, 스트리밍 티켓을 발급 시점에만 검증하고 사용 시점엔 재검증하지 않던 문제, HTTP Range 요청 파서의 suffix-range/다중 range 처리 버그
- reactor에서 `flatMap(m -> Mono.empty())` 패턴이 값이 있어도 항상 빈 `Mono`를 반환해버려서, 뒤에 붙인 `switchIfEmpty`(거부 응답)가 조건과 무관하게 항상 발동 — 조직 멤버 권한 체크가 사실상 **항상 403**으로 막히던 버그. VM/Port/Collaboration 세 서비스에 동일한 실수가 반복돼 있어 한 번에 일괄 수정
- 탈퇴한 계정의 이메일로 재가입하면 정상적으로 새 계정이 만들어져야 하는데 중복 이메일로 판단해 409를 반환하던 버그

### CORS / 라우팅

- Caddy로 CORS를 일원화하기 전까지는, 서비스별 로컬 CORS 설정에서 OPTIONS preflight가 인증 필터에 막히거나, dev 프로필이 없을 때 CORS 빈이 비활성화되지 않는 등 서비스마다 미묘하게 다른 문제가 반복
- 어드민 프론트 라우트가 파일 기반 라우팅상 `/admin/*`로 시작하면 백엔드 어드민 API 경로(`/admin/users`, `/admin/vms`)와 URL이 겹치는 문제 → 프론트 라우트를 `/admin` 이외의 경로로 완전히 분리하고, 필요 시 어드민 API를 별도 도메인으로 분리할 수 있는 옵션도 추가
- Ops 서비스의 공개 API 경로가 `/api/vms` → `/api/ops`로 바뀌었다가 Caddy 라우팅 규칙과 계속 충돌해 다시 `/ops`로 정리 — 경로 프리픽스는 각 서비스가 독립적으로 정할 게 아니라 리버스 프록시 라우팅 규칙과 맞춰서 먼저 확정해야 한다는 교훈

### VM 프로비저닝 (Proxmox / Cloudflare)

- Proxmox가 자체 서명 인증서를 쓰기 때문에 기본 WebClient SSL 검증이 걸려 연동 자체가 안 되던 문제 → 검증 비활성화(사설 네트워크 내부 통신이라 감수)
- VM 클론 태스크가 끝나기 전에 설정(config)을 먼저 건드리거나, 존재하지 않는 pool 파라미터를 넘겨 taskId가 null로 돌아오던 클론 순서 버그 → 클론 완료 대기 → 설정 순서로 재배치
- VM 이름이 Proxmox/DNS가 요구하는 서브도메인 형식을 만족하지 않으면 클론 요청 자체가 실패 → 생성 전 형식 검증 추가
- Cloudflare CNAME 등록 요청을 `Map<String, String>`으로 만들어 보내면 `proxied`(boolean) 값이 문자열로 직렬화돼 API가 400을 반환하던 문제 → `Map<String, Object>`로 교체
- VM cloud-init 설정에 DNS 서버(nameserver)가 아예 빠져 있어서, 게이트웨이가 DNS를 포워딩해주지 않는 네트워크에서는 VM 내부의 모든 도메인 조회(git clone, curl 등)가 실패하던 문제 — 실기 테스트에서 발견, 신규 생성 VM부터 적용(기존 VM은 수동 조치 필요)

### Ops / 배포 파이프라인

- Docker 설치를 `curl ... | sh` 파이프로 실행했는데, 파이프의 종료 코드는 마지막 명령(`sh`)만 반영하기 때문에 `curl`이 네트워크 오류로 실패해도 전체가 성공으로 오판되던 문제 → 임시 파일로 받아 각 단계를 `&&`로 연결하고 실제 설치 여부까지 확인
- Docker 설치는 성공해도 접속 계정을 `docker` 그룹에 자동으로 넣어주지 않아, 이후 모든 docker 명령이 "permission denied"로 실패하던 문제(Docker 관리 화면 + 배포 파이프라인 전체가 영향받음)
- 갓 생성된 VM은 cloud-init이 부팅 직후 자체적으로 `apt-get`을 실행 중이라 dpkg 락을 잡고 있어서, 곧바로 Docker를 설치하려 하면 "Unable to acquire the dpkg frontend lock"으로 실패하던 문제 → cloud-init 완료 대기 후에도 락이 남아있으면 일정 간격으로 재시도
- Ed25519 관리 키를 생성할 때 사용 중인 JSch 포크가 레거시 PEM 포맷을 지원하지 않아 `UnsupportedOperationException` 발생 → Ed25519는 OpenSSH v1 포맷으로만 표현 가능하다는 걸 확인하고 그 포맷으로 저장하도록 변경
- 배포 SSE 스트림이 완료/타임아웃/에러로 끝나는 시점에, 서블릿 컨테이너가 다른 스레드에서 ASYNC 디스패치를 필터 체인에 다시 흘려보내는데 이 시점엔 `SecurityContext`가 없어 인가 필터가 인증 안 된 요청으로 오판 → 이미 커밋된 SSE 응답이 깨져 브라우저에 `ERR_HTTP2_PROTOCOL_ERROR`로 나타나던 문제 → ASYNC/ERROR 디스패치는 최초 REQUEST 디스패치에서 이미 인증을 마쳤으므로 인가 재검사 대상에서 제외
- AI 기반 배포 스펙 생성이 백엔드 런타임 매니페스트가 전혀 없는 정적 HTML/CSS/JS 사이트를 Node.js로 오분류해, 존재하지 않는 포트·헬스체크를 지어내던 문제 → 결정론적 저장소 분석(매니페스트 기반 규칙 판정)을 AI 호출 앞에 두는 구조로 근본 해결 (위 "배포 파이프라인" 섹션)

### 데이터베이스 / 직렬화

- PostgreSQL은 `ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS`를 지원하지 않는다는 걸 뒤늦게 발견 → `pg_constraint` 조회 후 없을 때만 추가하는 `DO $$` 블록으로 교체했는데, 이번엔 R2DBC 드라이버의 SQL 파서가 dollar-quote(`$$`) 블록을 제대로 못 읽는 문제가 새로 발생 → 결국 `CREATE UNIQUE INDEX IF NOT EXISTS`처럼 애초에 조건부 문법을 지원하는 형태로 스키마 마이그레이션 패턴 자체를 바꿈
- `spring.jackson.serialization.*` 설정 키는 소문자 케밥이 아니라 대문자 스네이크케이스(`WRITE_DATES_AS_TIMESTAMPS`, `INDENT_OUTPUT`)로 써야 인식된다는 걸 모르고 적용이 안 되던 문제
- Spring Data `Page` 객체를 그대로 API 응답으로 반환하면 Jackson 직렬화 결과가 불안정해서, 처음엔 커스텀 DTO로 감쌌다가 → Jackson 설정(`default-property-inclusion`, 타임스탬프 포맷)을 제대로 잡은 뒤에는 다시 `Page`를 직접 반환하도록 정리(장기적으로 페이지네이션 확장이 쉬운 형태)

### 프론트엔드

- SSE 재연결 훅이 콜백을 매 렌더마다 새로 캡처해서 연결-해제-재연결이 반복되는 루프에 빠지던 문제 → 콜백을 ref로 분리하고, 재시도 횟수 상한과 토큰 없을 때 즉시 비활성화하는 가드 추가
- Cloudflare Tunnel의 idle timeout(약 100초) 때문에 오래 열어두는 SSE 연결이 524로 끊기던 문제 → 타임아웃 직전(99초)에 클라이언트가 선제적으로 재연결하도록 처리, 수동 동기화 버튼도 추가
- `useSearchParams`를 Suspense 경계 없이 사용해 프로덕션 빌드가 실패하던 문제
- 백엔드에 VM 상태 enum 값(`PENDING`/`BOOTING`/`FAILED`/`DELETING` 등)이 추가될 때마다 프론트 타입 정의가 따라가지 못해 특정 상태에서 화면이 깨지던 문제 — 여러 차례 반복되며 타입 동기화의 중요성을 재확인
- Tailwind v4가 브라우저의 시스템 다크모드를 자동으로 따라가면서 의도치 않게 배경이 검게 바뀌던 문제 → `color-scheme: light`로 고정
- `204 No Content` 응답에도 JSON 파싱을 시도해 에러가 나던 문제
- HTML `pattern` 속성의 정규식에서 문자 클래스(`[...]`) 안에 이스케이프 없는 하이픈을 그대로 쓰면 Chrome의 새 정규식 엔진(v-flag 모드)에서 항상 파싱 에러가 나는 브라우저 호환성 문제 — 여러 입력 필드(서브도메인 체크 등)에서 반복적으로 발견되어 동일한 방식으로 일괄 수정

### 플랜 / 요금제

- Role(권한)과 Plan(요금제)이 같은 enum(FREE/PRO/ADMIN)에 뒤섞여 있어서 "권한이 FREE"라는 의미상 모순이 존재하던 문제 → Role은 USER/ADMIN으로, Plan은 별도 도메인으로 완전히 분리
- 인스턴스 생성 화면의 FREE/PRO 최대 VM 대수가 프론트에 하드코딩돼 있어 실제 정책 값과 어긋나던 문제 → 사용량 조회 API가 내려주는 값을 그대로 쓰도록 변경
- 플랜 변경 요청 기능 자체는 JPA ID 자동 생성과 수동 설정 충돌(낙관적 락 예외), API 경로 불일치, 목록 응답의 `Page` 직렬화 문제 등을 거치며 여러 차례에 걸쳐 안정화

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
