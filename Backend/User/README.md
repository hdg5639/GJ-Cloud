# User Service

GamjaBox 사용자 프로필, SSH 공개키, 플랜·사용량, 업그레이드 요청, 사용자 설명서 CMS와 사용자 문의를 담당하는 Spring Boot 서비스다.

> 전체 시스템 구성은 [프로젝트 루트 README](../../README.md)를 참고한다.

## 책임 범위

- 닉네임과 프로필 이미지 관리
- SSH 공개키 등록·생성·삭제와 VM 서비스용 내부 조회
- FREE/PRO 플랜과 사용량 조회
- 플랜 업그레이드 요청 및 관리자 승인
- 사용자·관리자 계정 조회와 정지·활성화
- Markdown/GFM 기반 Docs CMS와 문서 이미지 저장
- 사용자 문의 접수·본인 이력 조회와 관리자 답변·상태 관리

인증 자격 증명은 Auth가, VM과 조직 데이터는 VM 서비스가 소유한다. User는 JWT를 검증하고 필요한 경우 두 서비스의 내부 API를 호출한다.

## 기능 흐름

### 프로필과 SSH 키

```text
Auth 회원가입
  → 서비스 토큰으로 POST /internal/profiles
  → 기본 프로필 생성

사용자 SSH 키 등록
  → 공개키 포맷·fingerprint 검증
  → VM 생성 시 VM 서비스가 내부 API로 키 조회
```

개인키를 직접 생성하는 API는 사용자에게 한 번만 반환해야 하며 서버가 평문 개인키를 장기 보관하지 않는 전제를 유지한다.

### Docs CMS

- 사용자는 발행된 문서만 목록·카테고리·상세 API로 조회한다.
- 관리자는 초안 작성, 수정, 발행·발행 취소, 추천·정렬·태그를 관리한다.
- 본문은 Markdown 원문으로 저장하고, 이미지는 JPEG/PNG/WebP/GIF 시그니처를 검증한다.
- 이미지 파일은 컨테이너 재배포 후에도 유지되는 외부 볼륨에 저장한다.

### 사용자 문의

- 사용자는 기술·계정·플랜·설명서·기타 문의를 접수하고 본인의 문의와 관리자 답변만 조회한다.
- 설명서에서 시작한 문의는 문서 slug와 제목을 함께 저장해 문의 맥락을 유지한다.
- 관리자는 ControlBox에서 접수됨·답변 완료·종료 상태를 조회하고 답변, 종료와 재오픈을 처리한다.
- 사용자 ID와 이메일은 JWT principal에서 가져오며 요청 본문 값으로 신뢰하지 않는다.
- 문의 내용과 답변은 각각 4,000자로 제한하고 비밀번호·토큰·개인키 입력 금지를 UI에서 안내한다.

## API 영역

| 영역 | 대표 경로 | 설명 |
|---|---|---|
| 프로필 | `/users/profile` | 조회·수정·프로필 이미지 업로드 |
| SSH 키 | `/users/ssh-keys` | 목록·등록·생성·삭제 |
| 사용량 | `/users/usage` | 사용자 플랜과 리소스 사용량 |
| 플랜 요청 | `/users/{userId}/upgrade-requests` | 업그레이드 요청·조회·취소 |
| 사용자 Docs | `/users/docs`, `/users/docs/{slug}` | 발행 문서 검색·카테고리·상세 |
| 사용자 문의 | `/users/support-inquiries` | 문의 접수·본인 이력 조회·종료 |
| Docs 이미지 | `/users/docs/images/{filename}` | 공개 문서 이미지 제공 |
| 관리자 사용자 | `/admin/users/**` | 계정·플랜·상태 관리 |
| 관리자 Docs | `/admin/docs/**` | 문서 CRUD, 발행, 이미지 업로드 |
| 관리자 Docs 이미지 | `/admin/users/docs/images/{filename}` | ControlBox 도메인용 공개 문서 이미지 조회 |
| 관리자 문의 | `/admin/users/support-inquiries/**` | 전체 문의 조회·답변·상태 관리 |
| 내부 API | `/internal/profiles`, `/internal/ssh-keys/**`, `/internal/automation/**` | 서비스 간 프로필·키·플랜 조회 |

ControlBox 사용자 목록은 `/admin/users/page?page=1&size=50`으로 DB 페이징하며, VM 목록의 현재 페이지 소유자만 `/admin/users/batch?userIds=...`로 일괄 조회한다. 기존 `/admin/users` 전체 목록은 호환성을 위해 유지하지만 신규 화면에서 사용하지 않는다. 프로필 검색은 인덱스를 탈는 prefix 검색을 먼저 실행하고, 결과가 부족할 때만 기존 contains 검색으로 보완한다.

Docs 목록은 `MEDIUMTEXT` 본문을 로드하지 않는 read-only summary 엔티티를 사용한다. 공개 목록은 `/users/docs/page`에서 18건, ControlBox는 `/admin/docs/page`에서 20건씩 DB 페이징하며, 상세 화면은 같은 카테고리 navigation 최대 100건만 받는다. 한국어 검색은 `(title, summary, category)`와 `tag` 각각의 `WITH PARSER ngram` FULLTEXT index를 우선 사용하고, 1글자이거나 FULLTEXT 결과가 없을 때만 contains 검색으로 fallback한다. 카테고리 필터·집계와 발행·추천·정렬·발행 시각, 관리자 최근 수정 순서는 각 복합 인덱스로 처리한다.

## 데이터와 보안

- MySQL: 프로필, SSH 키, 플랜 요청, Docs 문서, 사용자 문의와 답변
- 파일 저장소: 프로필 이미지와 Docs 이미지
- 사용자 API: JWT 인증 필요
- `/admin/**`: `ADMIN` 역할 필요
- `/internal/**`: 목적별 service token과 audience/scope 검증
- 업로드: 파일 크기, 확장자뿐 아니라 실제 파일 시그니처 검증

## 환경변수

| 분류 | 변수 | 용도 |
|---|---|---|
| DB | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL 연결 |
| 인증 | `AUTH_SERVER_URL`, `USER_SERVICE_CLIENT_SECRET` | JWKS·Token Exchange와 서비스 신원 |
| VM 연동 | `VM_SERVICE_URL` | 사용량과 VM 관련 내부 조회 |
| 프로필 이미지 | `PROFILE_IMAGE_STORAGE_PATH`, `PROFILE_IMAGE_PUBLIC_URL_PREFIX` | 저장 경로와 공개 URL prefix |
| Docs 이미지 | `DOCS_IMAGE_STORAGE_PATH`, `DOCS_IMAGE_PUBLIC_URL_PREFIX` | 저장 경로와 공개 URL prefix |

루트 Compose의 기본 경로 대응은 다음과 같다.

| 데이터 | 호스트 영속 경로 | 컨테이너 저장 경로 | 공개 URL prefix |
|---|---|---|---|
| 프로필 이미지 | `/opt/gamjabox/data/profile-images` | `/data/profile-images` | `/users/uploads/profile-images` |
| Docs 이미지 | `/opt/gamjabox/data/docs-images` | `/data/docs-images` | `/users/docs/images` |

`PROFILE_IMAGE_HOST_PATH`와 `DOCS_IMAGE_HOST_PATH`는 Compose 볼륨의 호스트 경로이고 애플리케이션 내부 저장 경로가 아니다. 운영 이미지 경로는 반드시 영속 볼륨에 연결하고, `*_STORAGE_PATH`와 볼륨의 컨테이너 경로를 동일하게 유지한다. 공개 URL prefix는 Caddy의 `/users` 라우팅과 맞아야 하며, 어긋나면 업로드는 성공해도 브라우저 표시가 실패한다. 전체 값 예시는 루트 `env.example`을 기준으로 한다.

## 로컬 실행과 검증

요구사항은 JDK 17과 MySQL이다. Auth와 VM 연동 기능까지 확인하려면 두 서비스도 접근 가능해야 한다.

```bash
cd Backend/User
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

```bash
./gradlew test
./gradlew clean build
```

서비스 상태는 `/actuator/health`에서 확인한다.

## 배포

- `develop`의 `Backend/User/**` 변경은 `deploy-user.yml`을 통해 개발 VM에 배포된다.
- `main` 변경은 `deploy-main-user.yml`을 통해 운영 VM에 배포된다.
- 배포 워크플로우는 새 이미지 헬스체크 실패 시 직전 이미지로 자동 롤백한다.
- 이미지 저장 볼륨은 컨테이너 교체와 무관하게 보존해야 한다.

## 운영 시 주의점

- Auth 계정과 User 프로필의 생성·삭제는 분산 작업이므로 실패 재시도 상태를 함께 확인한다.
- SSH 공개키 fingerprint 중복과 포맷 오류를 우회해 저장하지 않는다.
- Docs Markdown은 신뢰되지 않은 입력으로 취급하고 포털 렌더링 정책을 함께 유지한다.
- 관리자 UI가 숨겨져 있다는 사실은 권한 검증이 아니다. 관리자 API에서 역할을 반드시 재검사한다.
