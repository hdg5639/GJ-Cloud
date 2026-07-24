FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
# RepositorySnapshotBuilder가 배포 스펙 생성 전에 대상 저장소를 이 컨테이너 안에서 직접 얕은 클론(git clone)해
# 결정론적으로 분석하기 위해 필요 (VM으로의 SSH 배포 체크아웃과는 별개의 로컬 분석 전용 용도)
RUN apt-get update && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
