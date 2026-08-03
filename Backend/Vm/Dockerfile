# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src
# Gradle 홈(/root/.gradle: 배포판 + 의존성 + 빌드 캐시)을 빌드 간 재사용해 재빌드를 크게 단축한다.
# 서비스별 캐시 id로 병렬 빌드 시 락 경합을 피한다(첫 빌드는 콜드라 여전히 느림).
RUN --mount=type=cache,target=/root/.gradle,id=gradle-vm ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
RUN apt-get update && apt-get install -y --no-install-recommends openssl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/build/libs/*.jar app.jar
COPY import-proxmox-cert.sh /import-proxmox-cert.sh
RUN chmod +x /import-proxmox-cert.sh
ENTRYPOINT ["/import-proxmox-cert.sh"]
