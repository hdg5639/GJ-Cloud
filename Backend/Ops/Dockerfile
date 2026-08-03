# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk AS builder
# 빌드 컨텍스트 = 리포 루트(compose.yaml의 context: .). build.gradle의 syncPreviewTemplate가
# ${projectDir}/../../Frontend/portal 에서 포털 preview-runtime 실물을 리소스로 굽기 때문에,
# 리포 레이아웃을 그대로 미러링(/build/Backend/Ops 기준 ../../ = /build)해서 필요한 포털 소스만 COPY한다.
WORKDIR /build/Backend/Ops
COPY Backend/Ops/gradlew .
COPY Backend/Ops/gradle gradle
COPY Backend/Ops/build.gradle .
COPY Backend/Ops/settings.gradle .
COPY Backend/Ops/src src
# Auto Preview 배포 아티팩트 번들용 포털 소스(syncPreviewTemplate가 읽는 것만).
COPY Frontend/portal/lib/types.ts /build/Frontend/portal/lib/types.ts
COPY Frontend/portal/components/ui /build/Frontend/portal/components/ui
COPY Frontend/portal/components/preview-runtime /build/Frontend/portal/components/preview-runtime
# Gradle 홈(/root/.gradle: 배포판 + 의존성 + 빌드 캐시)을 빌드 간 재사용해 재빌드를 크게 단축한다.
# 서비스별 캐시 id로 병렬 빌드 시 락 경합을 피한다(첫 빌드는 콜드라 여전히 느림).
RUN --mount=type=cache,target=/root/.gradle,id=gradle-ops ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
# RepositorySnapshotBuilder가 배포 스펙 생성 전에 대상 저장소를 이 컨테이너 안에서 직접 얕은 클론(git clone)해
# 결정론적으로 분석하기 위해 필요 (VM으로의 SSH 배포 체크아웃과는 별개의 로컬 분석 전용 용도)
RUN apt-get update && apt-get install -y --no-install-recommends git util-linux ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY --from=builder /build/Backend/Ops/build/libs/*.jar app.jar
RUN groupadd --system --gid 10001 ops && useradd --system --uid 10001 --gid ops --no-create-home ops
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app.jar"]
