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
| **VM 프로비저닝** | Proxmox 템플릿 클론 → Static IP 할당 → SSH/cloud-init 준비 검증 → Cloudflare 연동까지 자동화. SSE로 생성 상태 실시간 수신 |
| **SSH 접속** | VM마다 전용 서브도메인 자동 발급. 관리 키로 접속 준비 상태와 사용자 `authorized_keys`를 검증·복구하고, Cloudflare Zero Trust로 이메일 기반 접근 제어 |
| **포트 노출** | HTTP/TCP 포트를 Cloudflare Tunnel로 외부 노출. PUBLIC / PRIVATE 구분, PRO 플랜의 자동 ID 없는 커스텀 CNAME 지원 |
| **플랜 관리** | FREE / PRO 플랜 전환, 디스크 온라인 확장. 플랜 변경은 관리자 승인 후 반영 |
| **협업 (Organization)** | 팀 단위로 VM 공유. 메모·공지·요청 게시판, 역할별 권한(OWNER / ADMIN / MEMBER) |
| **실시간 메트릭** | CPU·메모리·네트워크·디스크 사용량을 Proxmox API로 수집, SSE 스트림으로 라이브 시각화 |
| **웹 SSH 콘솔** | 브라우저에서 바로 VM 터미널 접속(WebSocket + xterm.js). 로그인 세션과 분리된 일회용 티켓으로 인증 |
| **파일 브라우저** | VM 내부 파일 조회·업로드·다운로드·편집·삭제. 텍스트 편집, 이미지/오디오/비디오 미리보기(Range 스트리밍) 지원 |
| **배포 파이프라인** | Git 저장소 → 이미지 빌드 → 헬스체크 → 실패 시 자동 롤백. GitHub push 자동 재배포, VM 내 다중 앱 격리, 실행 이력 기반 재시도/수동 롤백, 배포 대상 완전 삭제, SSE 실시간 로그 |
| **AI 배포 스펙 생성** | 저장소를 결정론적으로 분석해 확신 가능한 경우(정적 사이트 등)는 AI 호출 없이 규칙 기반으로 확정, 애매한 경우만 구조화 출력으로 AI에 위임. 렌더링된 compose를 AI가 비차단으로 검수 |
| **Auto Preview** | OpenAPI에서 서비스 의미와 사용자 목표를 해석해 다중 API 시나리오를 컴파일하고 실제 백엔드 상태로 실행·검증. 실행 가능한 시나리오가 없으면 Operation Preview로 안전하게 폴백하며, 281종 Blueprint Parts와 동일 Runtime으로 VM에 배포 |
| **Docker 관리** | 비동기 단계별 설치와 진행 상태 폴링, VM 내부 컨테이너/이미지/네트워크/compose 스택 조회 및 제어 |
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
            ├── GitHub App + Webhook  (저장소 접근·push 자동 재배포)
            ├── VM 내부 SSH(JSch)     (git 체크아웃·이미지 빌드·compose 기동·헬스체크·롤백)
            └── OpenAI API            (배포 스펙 AI 자동생성/검수 — 결정론적 분석이 실패한 경우에 한해)
```

내부 API는 호출 목적에 따라 인증 문맥을 나눈다. 관리 키 발급처럼 순수 서비스 신원이 필요한 작업은 client-credentials로 발급한 audience/scope 제한 토큰을 사용하고, 사용자별 리소스 조회처럼 최종 사용자 문맥이 필요한 작업은 `sub`를 보존한 위임 체인에서 별도로 검증한다.

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
- Caddy (리버스 프록시, HTTPS 자동, CORS 일괄 처리, 정적 랜딩 페이지 서빙)
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
    BOOTING   →  Proxmox 템플릿 클론 → vmid 배정 → 사용자 키 + Ops 관리 키 주입
              →  DNS 설정 → 디스크 리사이즈 → VM 시작 → Guest Agent로 IP 확인
              →  관리 키 SSH 접속·cloud-init 완료·사용자 키 fingerprint 확인
              →  사용자 authorized_keys 누락 시 관리 키로 안전하게 복구
    RUNNING   →  Cloudflare: CNAME 등록 → Tunnel ingress 추가 → Zero Trust Access 생성
    │
    ▼  SSE (/vms/events/subscribe) 로 클라이언트 실시간 수신
```

SSH 준비 검사는 최대 10분 동안 재시도하며, 관리 키 인증과 사용자 공개키 반영까지 확인된 VM만 RUNNING으로 전환한다. 실패 시 FAILED로 전환되고 원인이 기록된다.

---

## 배포 파이프라인 (Ops)

VM을 만든 뒤 그 안에 실제 서비스를 올리는 영역. 두 가지 스펙 생성 경로를 지원한다.

- **Raw Compose 배포** — 사용자가 직접 작성한 docker-compose 스펙(환경변수, 라우트, 헬스체크 포함)을 그대로 배포
- **AI 보조 배포** — 저장소 URL만 주면 스펙을 자동 생성

생성 화면은 `방식 선택 → 저장소 설정 → 서비스 힌트/Compose 작성 → 검토 및 배포`의 4단계로 구성된다. 방문한 단계는 앞뒤로 이동할 수 있고 입력 상태가 유지된다. Raw Compose와 AI 방식 모두 모노레포의 배포 기준 디렉토리와 VM 내부 install path를 지정할 수 있다.

저장소도 두 방식 중 하나를 명시적으로 선택한다.

- **GitHub 저장소 연결** — GitHub App 설치 범위 안에서 저장소·브랜치를 선택하고 push 자동 재배포를 사용할 수 있다.
- **Git URL 단발성 배포** — 공개 URL 또는 일회성 PAT로 현재 배포만 실행하며 PAT를 저장하지 않는다.

AI 보조 배포는 매 요청마다 무조건 AI를 호출하지 않는다:

1. Ops 컨테이너가 대상 저장소를 로컬에 얕게 클론해 `package.json`/`pom.xml`/`build.gradle`/`requirements.txt` 등 매니페스트를 결정론적으로 분석
2. 백엔드 런타임 매니페스트가 전혀 없는 순수 정적 사이트처럼 확신 가능한 경우는 **AI 호출 없이** 규칙 기반으로 스펙 확정
3. 애매한 서비스만 골라 AI에 위임 — 이때도 자유 텍스트가 아니라 구조화 출력(스키마 강제)으로 응답을 받아 파싱 실패 자체를 줄임
4. 빌드/실행 명령은 허용된 전략(enum) 목록에서만 선택되고, 각 전략은 고정된 argv로만 Dockerfile에 반영됨 — AI나 사용자 입력이 임의 셸 명령으로 이어지는 경로 자체가 없음
5. 근거가 부족하면 스펙을 지어내는 대신 `NEEDS_INPUT`/`UNSUPPORTED`/`CONFLICT` 같은 명시적 상태로 응답
6. 최종 렌더링된 compose 원문을 AI가 한 번 더 비차단으로 검수(운영상 위험·누락 헬스체크 등 코멘트만 제공, 배포를 막지 않음). 전송 전 시크릿처럼 보이는 값은 마스킹

배포 설정은 일회성 실행이 아니라 **배포 대상(Deployment Target)** 으로 저장된다. VM 한 대에 여러 대상을 만들 수 있고, 각 대상은 Docker Compose 프로젝트·bare 저장소·release/current 디렉토리·이미지 태그·배포 락·Cloudflare 라우트를 별도 appId로 격리한다. 따라서 서로 다른 앱은 같은 VM에서도 워커 수 범위 안에서 병렬 배포할 수 있으며, 한 앱을 내리거나 갱신해도 다른 앱의 컨테이너와 라우트는 건드리지 않는다.

GitHub App으로 저장소를 연결하고 자동 배포를 켜면 지정 브랜치의 `push` 웹훅이 새 배포를 만든다. 브랜치 이름을 다시 조회해 배포하는 대신 웹훅의 정확한 commit SHA를 checkout하므로 요청한 커밋과 실제 산출물이 어긋나지 않는다. 같은 대상이 배포 중일 때 push가 연속으로 오면 실행을 무한히 쌓지 않고 가장 최신 SHA 하나만 pending으로 유지해 현재 실행 직후 이어서 배포한다. GitHub installation token은 실행 시마다 단기로 발급하며 PAT는 영속화하지 않는다.

배포 실행 중에는 이미지 빌드·태깅, DB 기반 SSE 실시간 로그(재접속 시 이벤트 재생), 헬스체크 실패 시 자동 롤백이 이뤄진다. 실패한 배포는 같은 스펙으로 재시도하거나 값을 수정 후 재배포할 수 있고, 과거 성공한 배포로 수동 롤백도 가능하다(재빌드 없이 해당 시점 이미지로 컨테이너만 재기동).

외부 노출을 선택하면 생성된 모든 CNAME이 배포 대상 카드에 링크로 표시된다. 기본 주소는 VM·포트 식별자를 포함하고, PRO 사용자는 사용 가능한 이름을 검증받아 자동 ID가 붙지 않는 커스텀 CNAME을 지정할 수 있다.

`내리기`와 `배포 대상 삭제`는 의도적으로 다르다. 내리기는 실행 중인 컨테이너와 해당 배포 이미지를 정리하되 대상을 유지한다. 대상 삭제는 VM이 RUNNING일 때 컨테이너, 대상이 만든 전체 이미지 이력, bare Git 저장소, release/current 디렉토리, install path 심볼릭 링크, Cloudflare 라우트를 제거하고 대상을 비활성화한다. 배포 이력은 감사 목적으로 남긴다.

---

## Auto Preview (Ops)

Auto Preview는 API만 구현된 서비스에 실제 사용자 서비스처럼 보이고 작동하는 테스트 화면을 자동으로 조립하는 Ops 기능이다. 단순히 엔드포인트마다 폼 하나를 만드는 것이 아니라, 서비스 설명과 OpenAPI 증거를 바탕으로 사용자의 목표를 추론하고 여러 API를 연결한 시나리오, 페이지, 모달, 상태 전이, 검증 단계까지 생성한다. 완성된 결과는 브라우저에서 실제 API를 호출해 시험하고 일반 배포 파이프라인으로 VM에 배포할 수 있다.

핵심 원칙은 다음과 같다.

1. **OpenAPI가 실행 계약의 정본이다.** AI가 존재하지 않는 API, 필드, 응답 경로를 임의로 만들어 실행 계층에 넣을 수 없다.
2. **시나리오를 먼저 만들고 UI를 나중에 투영한다.** 화면 모양을 먼저 정한 뒤 API를 끼워 맞추지 않는다.
3. **AI는 의미를 제안하고 Compiler가 실행 가능성을 결정한다.** AI 출력은 semantic stage와 서비스 이해에 집중하며 실제 HTTP binding은 결정론적으로 만든다.
4. **포털과 배포본은 같은 Runtime을 사용한다.** 프리뷰와 실제 배포 앱 사이에 기능이 어긋나는 이중 구현을 두지 않는다.
5. **완전한 자동 생성이 불가능해도 사용 가능한 범위까지 단계적으로 폴백한다.** 서비스형 시나리오, 규칙 기반 시나리오, 개별 Operation Preview 순서로 기능을 보존한다.

### 사용자 작업 흐름

Auto Preview 화면은 입력·미리보기·배포의 세 단계로 구성된다. 앞뒤 단계로 이동해도 현재 분석 결과와 사용자가 입력한 값은 유지된다.

#### 1단계: API와 서비스 정보 입력

OpenAPI는 다음 두 방식 중 정확히 하나로 입력한다.

- HTTPS OpenAPI URL: 서버가 문서를 직접 가져온다.
- 로컬 JSON/YAML 파일: 브라우저가 최대 5MB 파일을 텍스트로 읽어 분석 요청에 포함한다.

여기에 다음 정보를 선택적으로 더할 수 있다.

- Swagger UI, Redoc, 제품 문서 같은 **서비스 문서 페이지 URL**
- 서비스의 사용자, 목적, 핵심 기능을 설명하는 **서비스 설명**
- 반드시 테스트하고 싶은 행동 순서를 적는 **시나리오 의도**
- 생성 목적: `API_TEST`, `PRODUCT_LIKE`, `ADMIN`
- 초기 프리뷰 모드: 시나리오 중심, 추론 시나리오 중심, 개별 Operation 중심

분석 요청의 입력 제한은 다음과 같다.

| 입력 | 제한 |
|---|---:|
| OpenAPI URL | 2,048자 |
| OpenAPI JSON/YAML 원문 | 5,242,880자, 파서 기준 최대 5MB |
| 서비스 문서 페이지 URL | 2,048자 |
| 서비스 설명 | 2,000자 |
| 시나리오 의도 | 4,000자 |
| 선택 Capability | 최대 300개, ID당 160자 |

분석 버튼을 누르면 로딩 상태와 진행 중 표시가 즉시 나타난다. 입력한 OpenAPI 원문과 문서 HTML 원문은 분석용으로만 사용하며 AI 감사 로그나 Blueprint 스냅샷에 그대로 저장하지 않는다.

#### 2단계: 서비스 화면 확인과 재구성

실행 가능한 시나리오가 하나라도 있으면 **서비스 화면**이 기본 탭으로 열린다. 시나리오 디버거나 엔드포인트 목록은 보조 도구이며, 사용자는 처음부터 실제 제품형 화면을 확인한다.

오른쪽의 독립 스크롤 분석 패널에서는 다음 정보를 볼 수 있다.

- 엔진이 이해한 서비스 유형, 주요 Actor, Entity, 사용자 Goal
- 서비스 이해에 사용된 입력 출처
- 감지된 API Capability와 카테고리
- 생성된 페이지, Flow, Scenario와 아직 해결하지 못한 항목
- 정적 검증 경고와 AI 검수 결과

사용자는 API 카테고리를 태그로 선택하고 “사용자가 상품을 비교한 뒤 장바구니에 넣고 결제 직전까지 이동”처럼 원하는 흐름을 자연어로 입력해 다시 생성할 수 있다. 재생성은 단순히 모달 하나만 바꾸는 작업이 아니다. 선택 범위에 맞춰 서비스 이해, Scenario, 페이지 경계, Flow, Blueprint Parts를 모두 다시 계산한다. 인증 Capability와 선택된 기능의 의존 Capability는 누락되지 않도록 자동으로 포함한다.

AI 검수 결과는 읽기 전용 조언으로 끝나지 않는다. 검수 내용을 바탕으로 구조화된 Page Plan 수정안을 요청하고, 사용자가 적용할 항목을 선택한 뒤 검증된 patch만 현재 결과에 반영할 수 있다.

#### 3단계: VM 배포

사용자는 배포 대상 이름과 실제 API Base URL을 지정한다. 배포 요청이 승인되면 현재 Scenario·Page Plan·Flow·Binding·Blueprint를 다시 검증하고 공용 Runtime이 들어간 Vite + React 프로젝트를 생성한 뒤 기존 비동기 배포 파이프라인에 전달한다. 배포 로그 화면에서도 포털 상단 내비게이션을 유지하므로 사용자가 Auto Preview 첫 화면으로 강제 이동하지 않고 원하는 위치로 이동할 수 있다.

### 전체 처리 파이프라인

```text
OpenAPI HTTPS URL 또는 JSON/YAML 파일
서비스 설명 + 시나리오 의도 + 선택한 Capability + 문서 페이지
    │
    ▼
입력 보안 검사와 문서 정규화
    ├─ OpenAPI 3.x 파싱
    ├─ operation/parameter/request/response/security 증거 추출
    ├─ 외부 $ref·redirect·내부망 접근 차단
    └─ 서비스 문서 본문 추출
    │
    ▼
전체 Capability Catalog 생성
    ├─ LIST/DETAIL/CREATE/UPDATE/DELETE/LOGIN
    ├─ QUERY/MUTATION/COMMAND/AUTH/METRIC/EVENT_STREAM/FILE_TRANSFER/WORKFLOW
    ├─ 검색·페이지네이션·응답 배열·ID·토큰 경로 감지
    └─ 위험도·자동화 정책·Capability 의존성 계산
    │
    ▼
활성 Capability Scope 계산
    ├─ 사용자가 선택한 카테고리
    ├─ 인증 Capability 자동 포함
    └─ 의존성 closure 자동 포함
    │
    ▼
서비스 의미 계획
    ├─ 서비스 유형·Actor·Entity·Goal 추론
    ├─ AI semantic Scenario 제안
    └─ 실패·저신뢰·실행 불가 시 규칙 기반 planner 폴백
    │
    ▼
Scenario Compiler
    ├─ semantic stage를 실제 Capability에 연결
    ├─ path/query/header/body/response extraction binding 생성
    ├─ state producer/consumer와 검증 단계 구성
    └─ 그래프·위험 작업·API 참조 hard validation
    │
    ▼
Page Plan · Flow · UI Projection
    ├─ 자연스러운 화면 경계와 내비게이션 생성
    ├─ 단일 페이지 작업공간 또는 다중 페이지 제품 구조 선택
    └─ Stage마다 content/interaction/presentation 계약 생성
    │
    ▼
Blueprint Retrieval · Composition
    ├─ Manifest/Elasticsearch에서 호환 Parts 검색
    ├─ 목적·데이터 shape·surface·risk 기준 hard filter
    ├─ 다양성을 고려해 전역 조합
    └─ 충돌 그룹만 국소 재선택
    │
    ▼
라이브 Product Runtime
    ├─ 실제 API 호출과 상태 전달
    ├─ 여러 모달·페이지·검토·확인·추적 흐름 실행
    ├─ 재시도·건너뛰기·취소·bounded polling
    └─ Developer Inspector에서 요청/응답/추출/검증 확인
    │
    ▼
배포 전 최종 컴파일
    ├─ 미사용 엔드포인트 제거
    ├─ Flow ID 정규화와 전체 계약 재검증
    ├─ 공용 Runtime + Blueprint JSON 아티팩트 생성
    └─ DeploymentTarget 생성 후 기존 VM 배포 파이프라인 실행
```

### 1. OpenAPI 수집과 정규화

`OpenApiNormalizer`는 입력 방식에 관계없이 같은 내부 모델을 만든다.

- OpenAPI 3.x 문서만 허용한다.
- JSON 파싱을 먼저 시도하고 실패하면 안전한 YAML 파서로 처리한다.
- 원격 URL은 HTTPS만 허용하며 loopback, site-local, link-local, multicast, any-local 주소와 클라우드 메타데이터 주소를 차단한다.
- HTTP redirect는 따라가지 않는다. redirect를 악용한 SSRF 우회도 허용하지 않는다.
- 원격 요청 제한 시간은 기본 15초, 문서 크기는 최대 5MB, 분석 operation 수는 기본 최대 300개다.
- 외부 `$ref`는 가져오지 않는다. 같은 문서 내부 참조만 제한된 깊이에서 해석한다.
- 공통 응답 envelope는 최대 4단계까지 벗기며, 응답 field path는 operation당 최대 60개까지 수집한다.

정규화 결과에는 `info.title`, `info.description`, version, server 목록, security scheme, operation ID, method/path, 태그와 설명, path/query parameter, request body field, response field/array/enum 증거가 포함된다. 이후 단계는 원본 OpenAPI 전체가 아니라 이 제한된 구조화 증거를 사용한다.

### 2. 서비스 문맥 결합

OpenAPI의 기술 정보만으로 “이 서비스가 누구를 위한 것인지” 알기 어려울 수 있다. `ServiceContextResolver`는 다음 입력을 출처와 함께 결합한다.

1. 사용자가 직접 작성한 서비스 설명
2. 사용자가 작성한 시나리오 의도
3. 선택적으로 가져온 서비스 문서 페이지
4. OpenAPI `info.description`
5. OpenAPI `info.title`

문서 페이지 수집기는 동일한 SSRF 정책을 적용하고 redirect를 차단한다. 기본 제한 시간은 10초, HTML 최대 크기는 1MB, 최종 추출 텍스트는 최대 6,000자다. JavaScript와 하위 리소스는 실행하거나 가져오지 않는다. `script`, `style`, `nav`, `footer`, `header`, 코드 블록 등 설명과 무관한 요소를 제거하고 title, meta description, `main`, `article`, `role=main`, Markdown 성격의 본문을 우선 추출한다.

결합된 서비스 문맥은 최대 12,000자로 제한된다. 응답에는 `resolvedServiceDescription`과 `serviceContextSources`가 함께 포함되어 사용자가 엔진이 무엇을 근거로 이해했는지 확인할 수 있다.

### 3. Capability Catalog와 선택 범위

정규화된 operation은 곧바로 UI 컴포넌트가 되지 않는다. 먼저 사람이 이해할 수 있는 기능 단위인 Capability로 변환한다.

| 속성 | 의미 |
|---|---|
| `capabilityId` | `{resource}.{type/action}` 형식의 안정적인 기능 ID |
| kind | `QUERY`, `MUTATION`, `COMMAND`, `AUTH`, `METRIC`, `EVENT_STREAM`, `FILE_TRANSFER`, `WORKFLOW` |
| type | CRUD형 기능의 `LIST`, `DETAIL`, `CREATE`, `UPDATE`, `DELETE`, `LOGIN` |
| evidence | Capability 판단에 사용한 실제 method/path/operation 증거 |
| fields | 입력 필드, 응답 필드, 배열 경로, enum 등 데이터 shape |
| extraction | access token, 선택 ID, 생성 ID, collection, total count 등을 꺼낼 경로 |
| dependencies | 먼저 실행하거나 state를 공급해야 하는 다른 Capability |
| risk | `SAFE`, `STATE_CHANGING`, `DESTRUCTIVE`, `IRREVERSIBLE`, `EXTERNAL_SIDE_EFFECT` |
| automation policy | 자동 실행 가능 여부와 사용자 확인 요구 수준 |
| confidence | 규칙과 증거에 따른 추론 신뢰도 |

첫 분석에서는 모든 Capability를 `availableCapabilities`로 반환한다. 사용자가 태그를 선택해 재생성하면 선택 항목을 중심으로 `activeCapabilityIds`와 실행용 `capabilities`를 다시 만든다. 존재하지 않는 ID는 거절하며, 로그인과 의존 Capability는 자동 포함한다. AI에 전달되는 operation 증거도 활성 범위로 잘라 불필요한 API가 시나리오를 오염시키지 않게 한다. 전체 Catalog는 계속 유지하므로 사용자는 나중에 범위를 다시 넓힐 수 있다.

### 4. 서비스 이해와 Scenario 의미 계획

AI planner의 계약 버전은 `scenario-planner-v1`이다. 입력은 최대 120개 Capability와 160개 operation 증거로 제한하며, AI는 다음과 같은 의미만 구조화해 제안한다.

- 서비스 archetype과 핵심 사용자
- 주요 Entity와 사용자의 최종 Goal
- Goal을 달성하기 위한 시나리오
- 각 단계의 의미 역할과 선후 관계
- 필요한 Capability와 예상 state

AI는 React 컴포넌트 ID, HTTP path, query 이름, JSON field path를 직접 결정하지 않는다. 이 값은 실제 Catalog와 OpenAPI 증거를 가진 Compiler만 선택한다.

Stage role은 `ENTRY`, `AUTHENTICATE`, `SELECT_CONTEXT`, `DISCOVER`, `INSPECT`, `SELECT`, `COMPARE`, `ACCUMULATE`, `CONFIGURE`, `PREPARE`, `REVIEW`, `COMMIT`, `WAIT`, `VERIFY`, `TRACK`, `RECOVER`, `CONTINUE`, `COMPLETE`로 구성된다. 예를 들어 구매형 서비스라면 탐색과 비교를 거쳐 선택을 누적하고, 검토 후 변경 요청을 실행하고, 후속 조회로 결과를 확인하는 목표 전체를 하나의 Scenario로 표현한다.

AI 출력은 최대 6개 Scenario, Scenario당 최대 16개 stage, 최대 40개 state key로 정규화한다. 규칙 기반 planner는 최대 8개 Scenario를 만들 수 있다. 모델 호출 실패, 낮은 신뢰도, 유효한 Scenario 부재, 컴파일 불가능한 출력은 요청 전체를 실패시키지 않고 규칙 기반 결과로 대체한다.

### 5. Scenario Compiler와 실행 안전성

`ScenarioCompiler`는 의미 계획을 실제 실행 계약으로 바꾼다.

- stage가 요구하는 Capability를 실제 Catalog에서 찾는다.
- path, query, header, body 입력을 사용자 입력 또는 이전 state에 binding한다.
- 응답에서 `authToken`, `selectedId`, `createdId`, 상태값, collection을 추출해 Scenario state에 저장한다.
- 다음 stage가 필요한 값을 어느 stage가 생산하는지 명시한다.
- 상태 변경 요청 뒤 상세 재조회, 목록 포함 여부, terminal status 확인 같은 verification을 붙인다.
- 인증 후 조회, 목록에서 선택 후 상세 확인, 생성·수정·명령 후 재조회 같은 실행 패턴을 구성한다.

Scenario schema version은 `1.0`, Runtime version은 `3.0.0`이다. Scenario는 최대 24개 stage를 가질 수 있다. 실행 전 검증기는 다음 문제를 hard error로 차단한다.

- 존재하지 않는 Capability 또는 operation 참조
- 중복 stage/flow ID
- 시작점에서 도달할 수 없는 stage
- 순환 그래프 또는 잘못된 다음 단계 연결
- 필요한 state의 producer 누락
- response extraction을 만들 수 없는 binding
- 선행 `REVIEW`가 없는 위험한 `COMMIT`
- 후속 `VERIFY` 또는 `TRACK`이 없는 상태 변경

완전히 실행 가능한 Scenario는 시나리오 프리뷰로, 일부만 가능한 결과는 제한된 시나리오 프리뷰로 내린다. Scenario를 안전하게 컴파일할 수 없어도 개별 operation을 호출하는 Operation Preview는 유지한다.

### 6. Page Plan, Flow, Journey와 UI Projection

컴파일된 Scenario 이후에만 화면 구조를 만든다. 같은 Scenario라도 서비스 성격과 단계 관계에 따라 자연스럽게 페이지를 나눌 수도 있고, 한 작업공간에 여러 기능을 모을 수도 있다.

- 최대 30개 Page Plan을 구성한다.
- 안내형 표현은 탐색, 상세, 입력, 비교, 검토, 추적, 완료를 별도 화면 경계로 나눈다.
- 압축형 표현은 의미와 실행 순서를 바꾸지 않고 관련 stage를 한 작업공간에 배치한다.
- 각 stage는 정확히 하나의 화면 경계에 속해야 한다.
- 표현 계층은 `capabilityId`, binding, state, `nextStageIds`를 수정할 수 없다.
- Page patch는 최대 50개 flow, 50개 operation, 페이지당 20개 navigation rule로 제한한다.

Flow는 실제 API 호출 단위를 순서대로 실행한다. Flow당 최대 20개 step을 허용한다. polling은 최대 300초, 3~60초 간격, 최대 100회로 제한해 무한 대기를 방지한다. 배포 전에는 Flow ID를 다시 정규화해 여러 페이지에서 같은 기능을 사용해도 중복 ID로 실패하지 않게 한다.

Journey Engine은 하나의 stage 안에서 여러 모달과 사용자 상호작용을 연결한다.

- 생성: `입력 → 실행 → 완료`
- 수정: `입력 → 영향 검토 → 실행 → 결과 확인`
- 삭제: `영향 검토 → 대상명 입력 확인 → 실행 → 완료`
- 도메인 작업: 환불, 재고 이동, 인시던트 승격, 배포 승격 등 목적별 모달 조합

위험도와 automation policy에 따라 확인 단계를 추가한다. 사용자가 이전 모달로 돌아가도 입력과 선택값을 유지하며, 실패한 실행은 같은 state로 재시도할 수 있다. Journey 정의도 중복 ID, 잘못된 연결, 순환, 도달 불가능 단계, 실행·성공 단계 누락을 미리 검증한다.

### 7. Blueprint Parts 검색과 전역 조합

`component-manifest.json`은 파츠 ID, family, mount point, slot, data shape, 지원 surface, capability 호환성, 목적, mode, risk policy의 단일 정본이다. 타입, Registry, Catalog 연결 코드는 Manifest에서 생성하므로 파츠를 추가할 때 TypeScript와 Java의 여러 분기를 손으로 중복 수정하지 않는다.

현재 Parts 구성은 다음과 같다.

| 종류 | 개수 | 예시 역할 |
|---|---:|---|
| ACTION | 16 | 주요 작업, 보조 작업, 위험 작업 트리거 |
| COLLECTION | 38 | 목록, 카드 그리드, 검색 결과, 비교 목록 |
| DASHBOARD | 36 | 서비스 홈, 요약, 상태, 추세 |
| DETAIL | 32 | 객체 상세, 활동, 메타데이터, 관계 |
| FEEDBACK | 14 | 로딩, 빈 상태, 경고, 성공/실패 |
| FORM | 18 | 생성·수정·필터·설정 입력 |
| LAYOUT | 28 | 제품형 셸, 분할 화면, 콘텐츠 구조 |
| MODAL | 41 | 입력, 검토, 확인, 결과, 도메인 작업 |
| NAVIGATION | 14 | 상단바, 탭, 단계, 문맥 이동 |
| THEME | 16 | 서비스 분위기별 색상·표면 토큰 |
| WORKFLOW | 28 | 준비, 실행, 추적, 복구, 완료 |
| **합계** | **281** | |

Blueprint 검색은 Manifest Registry를 정본으로 사용하고 Elasticsearch는 언제든 재생성 가능한 파생 인덱스로 사용한다. mount point, slot, runtime, data shape, risk policy를 hard filter한 뒤 관련도와 category, quality, stability 점수로 재랭킹한다. Elasticsearch가 비활성화되거나 응답하지 않으면 같은 계약을 적용하는 Registry 검색으로 자동 폴백한다. 검색 진단에는 사용 엔진, 후보 수, 제외 사유, 지연시간이 포함된다.

검색된 후보는 Block별로 바로 확정하지 않고 전역 Composition 단계를 거친다.

- `PICK_ONE`: 후보 중 정확히 하나
- `OPTIONAL_ONE`: 필요할 때 최대 하나
- `PICK_MANY`: 여러 파츠를 순서와 무관하게 선택
- `ORDER_MANY`: 여러 파츠를 순서까지 포함해 선택

검색 순위뿐 아니라 현재 요청과 누적 사용 빈도를 반영해 같은 표·카드·모달·레이아웃이 모든 페이지에서 반복되지 않도록 한다. 최종 검증에서 후보 그룹 이탈, 선택 개수 오류, 동일 family 과다 반복, modal/drawer presentation 편중, 페이지 간 layout 반복을 탐지한다. 충돌하면 정상 선택은 유지하고 문제 그룹만 최대 3회 국소 재선택한다. AI 파츠 추천도 허용 후보 안에서만 제안할 수 있으며 검증과 최종 결정은 결정론적이다.

`POST /ops/preview/blueprints/reindex`를 호출하면 Elasticsearch 인덱스를 현재 Registry에서 완전히 다시 만들 수 있다.

### 8. 제품형 Live Runtime

포털 프리뷰와 배포 앱은 동일한 React/TypeScript `preview-runtime`을 사용한다. Java가 배포용 React 코드를 문자열로 다시 구현하지 않는다. Ops 빌드가 공용 Runtime과 UI primitive를 리소스로 포함하고, 배포 시 Runtime 파일을 복사한 뒤 Scenario·Blueprint·Page Plan·Flow·Binding JSON만 주입한다.

프리뷰에는 세 가지 surface가 있다.

1. **서비스 화면**: 실제 고객용 제품처럼 페이지, 데이터, 행동, 여러 모달을 조합한 기본 화면
2. **Scenario Debugger**: stage, state, binding, 분기와 실행 상태를 개발자 관점에서 확인하는 화면
3. **Endpoint Preview**: 특정 operation을 직접 입력하고 호출하는 최하위 폴백 화면

제품 화면은 서비스 이해와 목적에 맞춰 Theme token을 자동 선택한다. 색상, 표면, 강조색, 상태색을 런타임 변수로 주입하므로 같은 Parts도 커머스, 운영 도구, 이벤트, 콘텐츠 서비스 등 서로 다른 분위기로 렌더링된다.

Scenario Runtime은 다음 기능을 제공한다.

- 여러 API를 순서대로 호출하고 응답을 다음 stage의 state로 전달
- 준비, 검토, 실행, 추적, 후속 조회 검증을 하나의 사용자 Goal로 실행
- 단계별 재시도, 선택적 건너뛰기, 전체 취소
- 제한된 polling과 terminal state 판정
- 페이지·모달을 뒤로 이동해도 입력, 선택, 호출 결과 유지
- 실패 지점에서 state를 보존한 재실행
- 요청·응답 로그와 실행 시간 기록

Developer Inspector는 현재 요청, 응답, 추출된 state, verification 결과, 소요시간을 화면과 동기화해 보여준다. 비밀번호, access token, API key, Authorization header 등 민감값은 마스킹한다.

브라우저 프리뷰는 사용자의 브라우저에서 대상 API를 직접 호출하므로 대상 API가 포털 Origin을 허용하지 않으면 CORS 정책에 의해 실패할 수 있다. 이는 OpenAPI 분석 성공 여부와 별개이며, 배포본에서는 배포된 Origin에 맞는 CORS 설정이 필요하다.

### 9. AI 검수와 구조화 수정

AI 검수는 현재 Blueprint를 자유 형식으로 덮어쓰지 않는다.

1. 현재 서비스 이해, Scenario, Page Plan, 검증 진단을 검수한다.
2. 문제와 개선 이유를 사용자에게 표시한다.
3. 사용자가 수정안 생성을 요청하면 허용된 Page Plan patch operation만 제안한다.
4. 사용자가 적용할 항목을 선택한다.
5. 서버가 patch 개수와 참조 무결성, page/flow/operation/navigation 제한을 검사한다.
6. 하나라도 유효하지 않으면 부분 적용하지 않고 전체 patch를 거절한다.
7. 검증을 통과한 수정만 새 분석 결과에 반영한다.

AI 호출 자체가 실패해도 기존 분석과 프리뷰를 사용할 수 있다. AI 감사 로그에는 model, input/output token 수, 성공 여부, 생성 종류만 저장하고 원문 프롬프트와 모델 응답은 저장하지 않는다.

### 10. 배포 아티팩트와 스냅샷

`POST /ops/{vmId}/preview/deploy`는 VM에 대한 `DEPLOY` 권한과 RUNNING 상태를 확인하고 `202 Accepted`로 비동기 배포를 시작한다.

배포 전 서버는 다음 작업을 다시 수행한다.

- Scenario가 실제 Capability Catalog만 참조하는지 검증
- Page 또는 Scenario에서 사용하는 runtime Capability만 남기고 orphan endpoint 제거
- 중복 Flow ID 정규화
- Page Plan, Flow, Binding, navigation 계약 검증
- Blueprint Block과 Component 호환성 검증
- 위험 operation의 확인 정책 검증

이후 `PreviewComposeArtifactBuilder`가 공용 Runtime을 복사하고 현재 구성을 JSON으로 주입한 Vite + React 프로젝트를 만든다. DeploymentTarget을 생성한 뒤 Git/Compose 배포와 동일한 배포 executor에 작업을 전달한다.

배포 시점의 API Base URL, Capability, Page, 인증 설정, Block, status, purpose, Page Plan, Flow, Binding, preview/generation mode, compiler/registry version, Scenario를 `PreviewBlueprintSnapshot`으로 남긴다. 따라서 나중에 배포 이력을 조회할 때 OpenAPI를 다시 가져오거나 AI 분석을 다시 실행할 필요가 없다.

데이터 보존 경계는 다음과 같다.

| 데이터 | 저장 여부와 목적 |
|---|---|
| OpenAPI URL | Custom Scenario와 Regression Suite 재검증이 필요한 경우 저장 |
| 업로드한 OpenAPI 원문 | 일반 분석·배포 스냅샷에는 원문 그대로 저장하지 않음 |
| 서비스 문서 HTML 원문 | 저장하지 않음 |
| 정규화 Capability/Page/Flow/Scenario | 분석 응답과 배포 Blueprint 스냅샷에 구조화 데이터로 포함 |
| 배포 API Base URL과 인증 전략 | 배포 앱 실행과 이력 재현을 위해 스냅샷에 포함 |
| Custom Scenario 자연어와 revision | 사용자 정의와 변경 이력을 위해 저장 |
| Regression 실행 결과 | stage 결과와 진단을 최대 512KB까지 저장 |
| AI 프롬프트·응답 원문 | 저장하지 않음 |
| AI model·token·성공 여부·생성 종류 | 비용·성공률 감사 목적으로 저장 |

### 11. PRO 커스텀 Scenario

자동 생성 결과에 없는 특수 업무 흐름은 PRO 사용자가 자연어로 별도 정의할 수 있다.

- Scenario 이름, 설명, 최대 4,000자의 자연어 요구사항 입력
- 해당 서비스의 현재 Capability Catalog에 맞춰 컴파일
- 검증을 통과한 revision만 활성화
- OpenAPI fingerprint가 바뀌면 기존 Scenario 재검증
- Scenario JSON 내보내기와 가져오기
- 모든 revision과 컴파일/검증 snapshot 보존

활성 Scenario를 수정할 때 기존 revision을 덮어쓰지 않는다. OpenAPI 변경으로 operation이나 field가 사라지면 자동 활성화하지 않고 재검증 결과를 남겨 사용자가 확인하게 한다.

### 12. PRO Scenario 회귀 테스트

Regression Suite는 활성 Custom Scenario와 revision을 묶어 실제 API에 반복 실행한다.

- 수동 실행과 CI 실행
- Suite별 OpenAPI, API Base URL, 초기 state, header 설정
- 상태 변경 operation 허용 여부와 fail-fast 설정
- 비동기 worker 실행
- 실행 이력과 stage별 상세 결과 저장
- 민감한 header/state/response 값 마스킹
- 실행 결과 본문 최대 512KB 저장

Suite를 만들 때 참조하는 Scenario가 활성 상태이고 revision과 OpenAPI fingerprint가 유효한지 확인한다. 실행은 mock이 아니라 지정한 API Base URL을 실제 호출한다. 상태 변경을 허용하지 않은 Suite에서는 위험 operation을 실행하지 않는다. Trigger type은 `MANUAL`, `CI`, `DEPLOYMENT`를 구분할 수 있다.

### 주요 API

| Method | Endpoint | 역할 |
|---|---|---|
| POST | `/ops/preview/analyze` | OpenAPI와 서비스 문맥 분석, Scenario/Page/Flow 생성 또는 재생성 |
| POST | `/ops/preview/blocks` | 분석 결과를 Blueprint Block으로 컴파일 |
| POST | `/ops/preview/parts/suggest` | 허용 후보 안에서 AI Parts 조합 제안 |
| POST | `/ops/preview/blueprints/search` | 조건에 맞는 Blueprint Parts 검색 |
| POST | `/ops/preview/blueprints/reindex` | Manifest Registry에서 Elasticsearch 인덱스 재구축 |
| POST | `/ops/preview/review` | 현재 결과 AI 검수 |
| POST | `/ops/preview/plan/propose` | 검수 결과 기반 Page Plan patch 제안 |
| POST | `/ops/preview/plan/apply` | 선택한 patch 검증 및 일괄 적용 |
| POST | `/ops/{vmId}/preview/deploy` | 현재 Preview를 VM 배포 대상으로 생성 |
| POST/GET | `/ops/preview/custom-scenarios` | PRO Custom Scenario 생성·목록 |
| POST | `/ops/preview/custom-scenarios/{id}/activate` | 검증된 Scenario revision 활성화 |
| POST | `/ops/preview/custom-scenarios/{id}/revalidate` | 현재 OpenAPI 기준 재검증 |
| GET | `/ops/preview/custom-scenarios/{id}/export` | Scenario JSON 내보내기 |
| POST | `/ops/preview/custom-scenarios/import` | Scenario JSON 가져오기 |
| POST/GET | `/ops/preview/regression-suites` | PRO Regression Suite 생성·목록 |
| POST | `/ops/preview/regression-suites/{id}/runs` | 수동 회귀 실행 |
| POST | `/ops/preview/regression-suites/{id}/ci/runs` | CI 회귀 실행 |
| GET | `/ops/preview/regression-suites/{id}/runs` | Suite 실행 이력 |
| GET | `/ops/preview/regression-suites/runs/{runId}` | 회귀 실행 상세 |
| DELETE | `/ops/preview/regression-suites/{id}` | Suite 비활성화 |

### 상태, 폴백과 현재 지원 범위

분석 상태는 `READY`, `NEEDS_INPUT`, `UNSUPPORTED`로 구분하고 생성 방식은 `SERVICE_AWARE`, `RULE_BASED`, `FALLBACK_CRUD`로 기록한다. 사용자가 요청한 모드와 실제로 가능한 모드가 다르면 실행 가능성이 높은 쪽으로 명시적으로 낮춘다. 진단 정보에는 폴백 이유와 해결되지 않은 Capability/Page/Binding, 검색 엔진, 제외 후보가 포함된다.

현재 인증 Runtime은 Bearer token과 API Key header/query를 지원한다. OpenAPI에서 token 추출 경로를 감지하지 못하면 사용자가 수동으로 지정할 수 있다. Cookie session처럼 브라우저 출처와 SameSite 정책에 강하게 결합된 인증, 외부 `$ref`, JavaScript 실행이 필요한 문서 페이지, Manifest 계약 밖의 임의 사용자 코드는 지원 범위에 포함하지 않는다.

상태 변경·삭제·외부 부작용 operation은 위험도와 automation policy에 따라 검토 및 명시적 확인 없이 자동 실행하지 않는다. 분석·AI 계획·화면 조립 중 일부가 실패하더라도 검증되지 않은 결과를 강제로 실행하지 않고, 마지막으로 안전하게 사용할 수 있는 프리뷰 계층을 반환한다.

---

## 도메인 구조

| 서브도메인 | 용도 |
|---|---|
| `gamjabox.cloud` (루트) | 마케팅 랜딩 페이지 (정적 HTML/CSS/JS, 프레임워크 없이 직접 작성) |
| `portal.*` | 사용자 포털 (Next.js) |
| `api.*` | 백엔드 API |
| `{vm-prefix}-{shortId}.*` | 사용자 VM SSH 접속 (VM 생성 시 자동 발급) |
| `{vm-prefix}-{shortId}-{portNickname}.*` | VM 추가 노출 포트 |
| `{customSubdomain}.*` | PRO 플랜 커스텀 공개 포트·배포 라우트 |

관리자 콘솔은 별도 비공개 도메인으로 분리되어 있으며 여기서는 다루지 않는다.

---

## 프로젝트 구조

```
GJ-Cloud/
├── .github/workflows/  develop·main 서비스별 배포 워크플로우
├── Backend/
│   ├── Auth/    Spring MVC — 인증·JWT·Refresh Token·이메일 인증
│   ├── User/    Spring MVC — 프로필·SSH 키·플랜
│   ├── vm/      Spring WebFlux — VM·포트·조직·협업·메트릭
│   └── Ops/     Spring MVC — 웹 SSH 콘솔·파일 브라우저·배포 파이프라인·AI 스펙 생성·DB 백업
├── Frontend/
│   └── portal/  Next.js — 사용자 포털 + 관리자 콘솔(같은 앱, 도메인으로 분리)
└── gamjabox-landing/  정적 마케팅 랜딩 페이지 (Vanilla HTML/CSS/JS)
```

---

## 개발 및 배포

백엔드는 서비스별 Gradle wrapper로, 포털은 Next.js 스크립트로 검증한다.

```bash
# 백엔드 예시
cd Backend/Ops
./gradlew test
./gradlew clean build

# 프론트엔드
cd Frontend/portal
npm ci
npm run lint
npm run build
```

GitHub Actions는 서비스별 path filter로 필요한 워크플로우만 실행한다. `develop`은 `gamjabox-dev`, `main`은 `gamjabox-prod` 라벨의 self-hosted Linux/X64 runner를 사용하며, 모든 배포 명령은 서버의 `/home/ubuntu/GJ-Cloud`에서 실행된다. 백엔드와 포털 배포는 새 이미지를 기동한 뒤 컨테이너 내부 IP의 health endpoint를 확인하고, 실패하면 보존한 이전 이미지로 자동 롤백한다.

루트와 각 서비스의 `compose.yaml`, `.env*`, 로컬 프록시 설정은 운영 자격증명을 포함할 수 있어 Git에서 제외한다. 공개 문서와 코드에는 환경변수 이름만 기록하고 실제 값은 서버 런타임 환경에서 관리한다.

Ops Blueprint 검색은 기본적으로 Registry fallback 상태다. Elasticsearch를 사용할 때만 `BLUEPRINT_SEARCH_ENABLED=true`, `ELASTICSEARCH_URL`, 필요 시 `ELASTICSEARCH_USERNAME`·`ELASTICSEARCH_PASSWORD`를 설정한다. `BLUEPRINT_SEARCH_REINDEX_ON_STARTUP=true`는 기동 시 전용 `BLUEPRINT_SEARCH_INDEX`를 Manifest 정본으로 재구축하므로 단일 Ops 인스턴스의 관리된 배포에서만 사용한다.

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
- 서비스 간 내부 API에는 "순수 서비스 신원"(client-credentials, 자기 자신만 호출 가능)과 "최종 사용자 위임"(호출한 서비스가 사용자 토큰을 그대로 포워딩, 받는 쪽은 sub만으로 본인 리소스 조회) 두 패턴이 섞여 있는데, User→VM 사용량 조회처럼 위임이 필요한 조합 하나가 순수 서비스 신원만 인정하는 체인에 묶여 있어 항상 401 — 이미 있던 다른 서비스 쌍(Ops↔VM 등)의 위임 전용 체인과 동일한 패턴으로 별도 분리해 해결
- 커스텀 인증 `WebFilter`/`Filter`를 `@Component`로 선언해뒀더니, SecurityConfig가 특정 체인에만 `.addFilterAt`으로 넣으려던 의도와 무관하게 Spring Boot가 전역 필터 체인에도 똑같은 인스턴스를 자동으로 중복 등록해버리는 문제 — 그중 하나(SSE 티켓 필터)는 실패 시 그냥 통과가 아니라 무조건 401을 내리는 로직이라, `/actuator/health`처럼 전혀 무관한 경로까지 전역 필터 실행 순서에 따라 간헐적으로 401을 받고 배포 헬스체크가 실패해 자동 롤백당하는 결과로 이어짐 → 필터 클래스들을 컴포넌트 스캔에서 빼고 SecurityConfig가 의존성만 주입받아 직접 생성해 넣는 방식으로 전환

### CORS / 라우팅

- Caddy로 CORS를 일원화하기 전까지는, 서비스별 로컬 CORS 설정에서 OPTIONS preflight가 인증 필터에 막히거나, dev 프로필이 없을 때 CORS 빈이 비활성화되지 않는 등 서비스마다 미묘하게 다른 문제가 반복
- 어드민 프론트 라우트가 파일 기반 라우팅상 `/admin/*`로 시작하면 백엔드 어드민 API 경로(`/admin/users`, `/admin/vms`)와 URL이 겹치는 문제 → 프론트 라우트를 `/admin` 이외의 경로로 완전히 분리하고, 필요 시 어드민 API를 별도 도메인으로 분리할 수 있는 옵션도 추가
- Ops 서비스의 공개 API 경로가 `/api/vms` → `/api/ops`로 바뀌었다가 Caddy 라우팅 규칙과 계속 충돌해 다시 `/ops`로 정리 — 경로 프리픽스는 각 서비스가 독립적으로 정할 게 아니라 리버스 프록시 라우팅 규칙과 맞춰서 먼저 확정해야 한다는 교훈

### CI/CD

- 배포 자동 롤백용 헬스체크가 `docker compose port`로 알아낸 호스트 매핑 포트에 `localhost`로 curl했는데, 그 포트를 호스트에 publish하지 않는 구성이면 대상 자체가 비어 있어 앱이 매번 정상 기동돼도 헬스체크는 100% 실패하고 매 배포가 자동 롤백당하던 문제 → 호스트 포트 publish 여부와 무관하게 항상 유효한 컨테이너 내부 IP로 직접 확인하도록 변경
- 리액티브 앱(R2DBC + Redis + WebClient 초기화)의 콜드 스타트 시간이 헬스체크 재시도 타임아웃 경계값과 겹쳐서, 정상적으로 뜬 이미지가 타이밍 때문에 실패로 오판돼 롤백당한 적 있음 → 재시도 타임아웃을 넉넉히 확대

### VM 프로비저닝 (Proxmox / Cloudflare)

- Proxmox가 자체 서명 인증서를 쓰기 때문에 기본 WebClient SSL 검증이 걸려 연동 자체가 안 되던 문제 → 처음엔 검증 자체를 비활성화했다가, 이후 보안 강화 과정에서 운영 환경엔 이 trust-all 코드 경로 자체를 아예 없애버려서(실수로도 켜질 수 없게) 그 대가로 프로비저닝 API 호출이 전부 TLS 핸드셰이크 단계에서 막히는 회귀가 발생 → trust-all을 되살리는 대신, 컨테이너 기동 시마다 현재 설정된 Proxmox 서버의 인증서를 그 자리에서 받아와 JVM 트러스트스토어에 이 인증서 하나만 등록하는 방식으로 정리 — Proxmox의 IP나 인증서가 나중에 바뀌어도 재배포 없이 컨테이너 재시작만으로 자동 반영됨
- VM 클론 태스크가 끝나기 전에 설정(config)을 먼저 건드리거나, 존재하지 않는 pool 파라미터를 넘겨 taskId가 null로 돌아오던 클론 순서 버그 → 클론 완료 대기 → 설정 순서로 재배치
- VM 이름이 Proxmox/DNS가 요구하는 서브도메인 형식을 만족하지 않으면 클론 요청 자체가 실패 → 생성 전 형식 검증 추가
- Cloudflare CNAME 등록 요청을 `Map<String, String>`으로 만들어 보내면 `proxied`(boolean) 값이 문자열로 직렬화돼 API가 400을 반환하던 문제 → `Map<String, Object>`로 교체
- VM cloud-init 설정에 DNS 서버(nameserver)가 아예 빠져 있어서, 게이트웨이가 DNS를 포워딩해주지 않는 네트워크에서는 VM 내부의 모든 도메인 조회(git clone, curl 등)가 실패하던 문제 — 실기 테스트에서 발견, 신규 생성 VM부터 적용(기존 VM은 수동 조치 필요)
- VM SSH 접속의 간헐적 `Auth fail for methods 'publickey'`를 `sshkeys` 이중 인코딩 문제로 오인해 인코딩을 제거했다가, Proxmox가 오히려 percent-encoded 값을 요구해 VM 생성이 400(`invalid urlencoded string`)으로 막힌 회귀가 발생 → 인코딩은 원복하고 결과 기반 준비 검증으로 방향을 전환
- VM 생성 완료를 Guest Agent의 IP 확인만으로 판정하면 cloud-init과 SSH 키 반영이 아직 끝나지 않은 상태도 RUNNING이 될 수 있었음 → 사용자 키와 VM별 Ops 관리 키를 함께 주입한 뒤 관리 키 SSH 접속, cloud-init 상태, 사용자 키 fingerprint를 최대 10분간 확인하고, `authorized_keys`에서 사용자 키가 누락된 경우 검증된 공개키만 관리 세션으로 복구한 후 RUNNING으로 전환

### Ops / 배포 파이프라인

- Docker 설치를 `curl ... | sh` 파이프로 실행했는데, 파이프의 종료 코드는 마지막 명령(`sh`)만 반영하기 때문에 `curl`이 네트워크 오류로 실패해도 전체가 성공으로 오판되던 문제 → 임시 파일로 받아 각 단계를 `&&`로 연결하고 실제 설치 여부까지 확인
- Docker 설치는 성공해도 접속 계정을 `docker` 그룹에 자동으로 넣어주지 않아, 이후 모든 docker 명령이 "permission denied"로 실패하던 문제(Docker 관리 화면 + 배포 파이프라인 전체가 영향받음)
- 갓 생성된 VM은 cloud-init이 부팅 직후 자체적으로 `apt-get`을 실행 중이라 dpkg 락을 잡고 있어서, 곧바로 Docker를 설치하려 하면 "Unable to acquire the dpkg frontend lock"으로 실패하던 문제 → cloud-init 완료 대기 후에도 락이 남아있으면 일정 간격으로 재시도
- Ed25519 관리 키를 생성할 때 사용 중인 JSch 포크가 레거시 PEM 포맷을 지원하지 않아 `UnsupportedOperationException` 발생 → Ed25519는 OpenSSH v1 포맷으로만 표현 가능하다는 걸 확인하고 그 포맷으로 저장하도록 변경
- 배포 SSE 스트림이 완료/타임아웃/에러로 끝나는 시점에, 서블릿 컨테이너가 다른 스레드에서 ASYNC 디스패치를 필터 체인에 다시 흘려보내는데 이 시점엔 `SecurityContext`가 없어 인가 필터가 인증 안 된 요청으로 오판 → 이미 커밋된 SSE 응답이 깨져 브라우저에 `ERR_HTTP2_PROTOCOL_ERROR`로 나타나던 문제 → ASYNC/ERROR 디스패치는 최초 REQUEST 디스패치에서 이미 인증을 마쳤으므로 인가 재검사 대상에서 제외
- AI 기반 배포 스펙 생성이 백엔드 런타임 매니페스트가 전혀 없는 정적 HTML/CSS/JS 사이트를 Node.js로 오분류해, 존재하지 않는 포트·헬스체크를 지어내던 문제 → 결정론적 저장소 분석(매니페스트 기반 규칙 판정)을 AI 호출 앞에 두는 구조로 근본 해결 (위 "배포 파이프라인" 섹션)
- Docker 설치 요청은 수 분 걸릴 수 있는데 HTTP 요청 스레드에서 그대로 블로킹하고 있어서, 중간 리버스 프록시가 응답을 오래 기다리다 연결을 끊으면 서버는 계속 실행 중인데 클라이언트엔 `Failed to fetch`만 뜨던 문제(배포 파이프라인과 동일한 문제/해법) → 요청은 즉시 응답하고 실제 작업은 전용 워커로 위임, 진행 상태는 클라이언트가 폴링해서 확인
- 그 설치 스크립트 자체도 저장소 등록~apt install까지 전 단계를 하나의 `&&` 체인으로 묶어 최대 10회 재시도하고 있어서, 마지막 단계에서 한 번만 실패해도 다음 시도가 GPG 키 재획득부터 전부 다시 실행되며 수동 설치(~1분) 대비 5분 이상 걸리던 문제 → 단계별로 나눠 각 단계만 개별 재시도하도록 재설계, 진행 중인 단계명도 실시간으로 노출
- 단계별 재설계 이후에도 설치가 간헐적으로 실패/타임아웃되던 문제를 더 파보니 겹친 원인이 여러 개였음: best-effort로 설계했던 `cloud-init status --wait` 대기가 일반 SSH 명령과 같은 짧은 공통 타임아웃을 쓰다 보니 시간 초과 예외가 그대로 던져져 "실패해도 계속 진행"이어야 할 단계가 설치 전체를 중단시켰고, 뒤이은 apt 단계들도 비대화형 실행 옵션 없이 짧은 타임아웃으로 도는 데다 cloud-init이 쥔 dpkg 잠금과 계속 경합했으며, 패키지 다운로드처럼 오래 걸리는 SSH 세션에 keepalive가 없어 NAT/방화벽에 유휴 연결로 끊기기도 함 → 단계별 타임아웃을 넉넉히 분리하고 `DEBIAN_FRONTEND=noninteractive` + `DPkg::Lock::Timeout`으로 잠금을 안전하게 기다리도록 apt 커맨드를 재구성, SSH 세션에 서버 alive 신호 추가. 설치 완료 판정도 `docker` 바이너리 존재 확인만으로는 데몬이 실제로 떠 있는지 보장 못 해서 `docker info` 응답까지 확인하도록 강화

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
- 사이드바 사용량 패널의 조회가 로그인 토큰에만 의존하는 effect였는데, 이 패널이 있는 대시보드 레이아웃은 페이지를 이동해도 리마운트되지 않는 Next.js 특성상 최초 로그인 이후로는 다시 조회되지 않던 문제(VM을 새로 만들어도 새로고침 전까진 개수가 그대로) → 현재 경로도 effect 의존성에 추가해 페이지 이동마다 재조회

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
- **GitHub App 자격증명** — 대상에는 저장소 ID·브랜치만 저장하고 installation token은 실행 시마다 단기로 발급. 직접 URL 방식의 PAT는 저장하지 않음
- **자동/정기 DB 백업 미구현** — 현재는 온디맨드 수동 백업만 지원

---

<p align="center">
  <sub>Built with ☕ on a home server</sub>
</p>
