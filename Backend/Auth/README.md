# Auth Service

GamjaBox의 사용자 인증, JWT 발급, Refresh Token Rotation, 이메일 인증과 계정 수명주기를 담당하는 Spring Boot 서비스다.

> 전체 시스템 구성은 [프로젝트 루트 README](../../README.md)를 참고한다.

## 책임 범위

- 회원가입, 로그인, 로그아웃, 회원 탈퇴
- 이메일 인증과 비밀번호 재설정·변경
- RS256 Access Token과 Refresh Token 발급·회전
- 서비스 간 client-credentials 토큰과 사용자 위임 Token Exchange
- 공개 JWKS 제공 및 인증 보안 감사 로그 기록
- 탈퇴 계정의 후속 삭제 작업 재시도와 미인증 계정 정리

Auth는 사용자 프로필이나 VM 데이터를 직접 소유하지 않는다. 회원가입 시 User 서비스에 프로필 생성을 요청하고, 탈퇴 시 User·VM 서비스의 데이터 정리를 조정한다.

## 주요 인증 흐름

```text
로그인
  → Access Token(단기 JWT) 응답
  → Refresh Token(httpOnly 쿠키) 저장
  → 만료 전 POST /auth/token/refresh
  → 기존 토큰 폐기 + 같은 token family의 새 토큰 발급
```

- Access Token은 포털 메모리에만 보관한다.
- 로그인 유지 OFF는 고정 만료, ON은 30일 Sliding 만료를 사용한다.
- 이미 회전된 Refresh Token이 재사용되면 해당 token family를 폐기한다.
- 서비스 토큰은 audience와 scope를 제한하고, 사용자 위임 토큰은 원래 `sub`를 보존한다.

## API 영역

| 영역 | 대표 경로 | 설명 |
|---|---|---|
| 사용자 인증 | `/auth/register`, `/auth/login`, `/auth/logout` | 가입·로그인·로그아웃 |
| 토큰 | `/auth/token/refresh`, `/auth/token/exchange`, `/auth/token/service` | 갱신·위임·서비스 토큰 |
| 키 공개 | `/auth/.well-known/jwks.json` | 다른 서비스의 JWT 서명 검증용 JWKS |
| 이메일 | `/auth/email/verify/**` | 인증 코드 발송·확인 |
| 비밀번호 | `/auth/password/**` | 재설정 코드, 비밀번호 재설정·변경 |
| 계정 | `/auth/withdraw` | 사용자 탈퇴 시작 |
| 관리자 | `/admin/security-audit-logs`, `/admin/account-deletion-jobs` | 감사 로그와 삭제 실패 작업 확인 |
| 내부 API | `/internal/users/{userId}/status` | 서비스 인증 기반 사용자 상태 변경 |

정확한 요청·응답 스키마는 실행 중인 서비스의 SpringDoc 문서를 기준으로 한다.

## 저장소와 백그라운드 작업

- MySQL: 사용자, Refresh Token 메타데이터, 감사 로그, 탈퇴 작업
- Redis: 인증 관련 단기 상태와 서비스 토큰 캐시
- Mail 서버: 이메일 인증과 비밀번호 재설정 메일
- 매시간: 만료된 미인증 사용자 정리
- 매일 03:00: 장기 정리 작업
- 5분 간격: 실패한 계정 삭제 작업 재시도

## 환경변수

실제 비밀값은 저장소에 커밋하지 않는다. 운영 설정은 `application-prod.yaml`의 placeholder를 외부 환경변수로 채운다.

| 분류 | 변수 | 용도 |
|---|---|---|
| DB | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL 연결 |
| Redis | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis 연결 |
| JWT | `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` | RS256 서명·검증 키 |
| 세션 | `JWT_REMEMBER_ME_REFRESH_TOKEN_EXPIRY` | 로그인 유지 Refresh Token 만료 기간 |
| 메일 | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | 인증 메일 발송 |
| 인증 기능 | `EMAIL_VERIFICATION_ENABLED` | 이메일 인증 기능 활성화 |
| 연동 URL | `USER_SERVICE_URL`, `VM_SERVICE_URL` | 후속 서비스 호출 주소 |
| 서비스 인증 | `USER_SERVICE_CLIENT_SECRET`, `VM_SERVICE_CLIENT_SECRET`, `OPS_SERVICE_CLIENT_SECRET` | 서비스별 client secret |

PEM 키는 줄바꿈이 보존되어야 한다. Base64나 한 줄 문자열로 전달하는 경우 현재 설정의 키 정규화 방식과 맞는지 먼저 확인한다.

## 로컬 실행과 검증

요구사항은 JDK 17과 MySQL, Redis다.

```bash
cd Backend/Auth
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

```bash
./gradlew test
./gradlew clean build
```

컨테이너 구성이 필요한 경우 이 디렉터리의 `compose.yaml`을 사용한다. 서비스 상태는 `/actuator/health`에서 확인한다.

## 배포

- `develop`의 `Backend/Auth/**` 변경은 `deploy-auth.yml`을 통해 개발 VM에 배포된다.
- `main` 변경은 `deploy-main-auth.yml`을 통해 운영 VM에 배포된다.
- 새 이미지 기동 후 Actuator 헬스체크가 실패하면 보존된 직전 이미지로 자동 롤백한다.

## 운영 시 주의점

- 400/401/403은 세션 무효일 수 있지만 네트워크 오류·429·5xx를 즉시 로그아웃으로 취급하면 안 된다.
- 여러 탭이 같은 회전형 Refresh Token을 동시에 갱신하지 않도록 포털의 Web Locks 동기화를 유지한다.
- JWT 개인키, 메일 비밀번호, 서비스 client secret은 로그에 출력하지 않는다.
- Auth·User·VM 서비스의 client id, secret, audience가 서로 다르면 내부 호출이 401/403으로 실패한다.
