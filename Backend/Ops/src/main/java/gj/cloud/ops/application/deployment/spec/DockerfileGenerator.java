package gj.cloud.ops.application.deployment.spec;

import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.springframework.stereotype.Component;

// build/artifact/run 분리 스키마(5절) + 허용목록 전략(6절) 기반 Dockerfile 생성.
// buildCommand/startCommand 같은 자유 문자열은 더 이상 존재하지 않음 — 모든 RUN/CMD 값은
// BuildRunStrategy의 "고정된" 케이스에서만 나오므로(이 클래스 밖에서 절대 인자를 주입하지 않음)
// AI나 사용자가 임의 셸 명령을 넣을 수 있는 경로 자체가 없음.
// 참고: build.strategy=DOCKERFILE(저장소에 이미 있는 Dockerfile 사용)인 서비스는 이 클래스를 호출하지 않고
// DeploymentSpecRenderer가 저장소의 기존 Dockerfile 경로를 그대로 참조함 — generate()는 그 경우 호출되지 않는다.
@Component
public class DockerfileGenerator {

    public String generate(ServiceSpec service) {
        if (service.build().strategy() == BuildRunStrategy.DOCKERFILE) {
            throw new IllegalStateException(
                    "DOCKERFILE 전략은 저장소의 기존 Dockerfile을 사용해야 하므로 generate() 호출 대상이 아닙니다 (렌더러 버그)");
        }
        return switch (service.artifact().type()) {
            case STATIC_DIRECTORY -> staticDirectory(service);
            case JAR -> jar(service);
            case PYTHON_APPLICATION -> pythonApplication(service);
            case CONTAINER_IMAGE -> containerImage(service);
            case UNKNOWN -> throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        };
    }

    // artifact=STATIC_DIRECTORY, build.runtime=NONE → 빌드 단계 없이 그대로 nginx로 서빙 (진짜 정적 사이트)
    // artifact=STATIC_DIRECTORY, build.runtime=NODEJS → Node로 빌드한 뒤 산출물만 nginx로 서빙 (Vite/CRA 등)
    private String staticDirectory(ServiceSpec service) {
        if (service.build().runtime() != RuntimeKind.NODEJS) {
            return """
                    FROM nginx:alpine
                    COPY . /usr/share/nginx/html
                    EXPOSE 80
                    """;
        }

        int nodeVersion = parseIntOrDefault(service.build().version(), 20);
        String outputPath = service.build().outputPath() != null ? service.build().outputPath() : "dist";
        String buildStep = buildStepFor(service.build().strategy());

        return """
                FROM node:%d-alpine AS build
                WORKDIR /app
                COPY package*.json ./
                RUN %s
                COPY . .
                RUN %s

                FROM nginx:alpine
                COPY --from=build /app/%s /usr/share/nginx/html
                EXPOSE 80
                """.formatted(nodeVersion, installStepFor(service.build().strategy()), buildStep, outputPath);
    }

    // artifact=JAR, build.runtime=JAVA → Maven/Gradle 멀티스테이지 빌드, 슬림 JRE 이미지로 실행
    private String jar(ServiceSpec service) {
        int javaVersion = parseIntOrDefault(service.build().version(), 21);
        String buildStep = buildStepFor(service.build().strategy());
        boolean maven = service.build().strategy() == BuildRunStrategy.MAVEN_PACKAGE;
        String jarGlob = maven ? "/app/target/*.jar" : "/app/build/libs/*.jar";
        int port = service.run().containerPort() != null ? service.run().containerPort() : 8080;

        return """
                FROM eclipse-temurin:%d-jdk AS build
                WORKDIR /app
                COPY . .
                RUN %s

                FROM eclipse-temurin:%d-jre
                WORKDIR /app
                COPY --from=build %s app.jar
                EXPOSE %d
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """.formatted(javaVersion, buildStep, javaVersion, jarGlob, port);
    }

    // artifact=PYTHON_APPLICATION, build.runtime=PYTHON → 단일 스테이지, gunicorn/uvicorn/django runserver로 실행
    private String pythonApplication(ServiceSpec service) {
        String pythonVersion = service.build().version() != null ? service.build().version() : "3.11";
        int port = service.run().containerPort() != null ? service.run().containerPort() : 8000;
        String installStep = service.build().strategy() == BuildRunStrategy.UV_SYNC
                ? "pip install --no-cache-dir uv && uv sync --frozen"
                : "pip install --no-cache-dir -r requirements.txt";
        String[] cmd = switch (service.run().strategy()) {
            case GUNICORN -> new String[]{"gunicorn", "main:app", "--bind", "0.0.0.0:" + port};
            case UVICORN -> new String[]{"uvicorn", "main:app", "--host", "0.0.0.0", "--port", String.valueOf(port)};
            default -> throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        };

        return """
                FROM python:%s-slim
                WORKDIR /app
                COPY . .
                RUN %s
                EXPOSE %d
                CMD %s
                """.formatted(pythonVersion, installStep, port, quoteJsonArray(cmd));
    }

    // artifact=CONTAINER_IMAGE, build.runtime=NODEJS → Node 서버 프로세스(Next.js SSR/Express/NestJS 등).
    // 컴파일된 별도 산출물(jar 같은)을 구분해 다루지 않고 컨테이너 자체가 곧 산출물인 경우.
    private String containerImage(ServiceSpec service) {
        if (service.build().runtime() != RuntimeKind.NODEJS) {
            throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }
        int nodeVersion = parseIntOrDefault(service.build().version(), 20);
        int port = service.run().containerPort() != null ? service.run().containerPort() : 3000;
        String[] runCmd = switch (service.run().strategy()) {
            case NPM_START -> new String[]{"npm", "start"};
            case PNPM_START -> new String[]{"pnpm", "start"};
            case YARN_START -> new String[]{"yarn", "start"};
            default -> throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        };

        if (service.build().strategy() == BuildRunStrategy.NONE) {
            // 빌드 단계 없이 바로 실행 (예: 순수 Node 스크립트, 빌드 트랜스파일이 필요 없는 경우)
            return """
                    FROM node:%d-alpine
                    WORKDIR /app
                    COPY package*.json ./
                    RUN npm ci --omit=dev
                    COPY . .
                    EXPOSE %d
                    CMD %s
                    """.formatted(nodeVersion, port, quoteJsonArray(runCmd));
        }

        String buildStep = buildStepFor(service.build().strategy());
        return """
                FROM node:%d-alpine AS build
                WORKDIR /app
                COPY package*.json ./
                RUN %s
                COPY . .
                RUN %s

                FROM node:%d-alpine
                WORKDIR /app
                ENV NODE_ENV=production
                COPY --from=build /app ./
                EXPOSE %d
                CMD %s
                """.formatted(nodeVersion, installStepFor(service.build().strategy()), buildStep,
                nodeVersion, port, quoteJsonArray(runCmd));
    }

    // 아래 두 메서드가 반환하는 값은 전부 컴파일 타임에 고정된 문자열 리터럴이다(enum 케이스별 switch) —
    // 외부(AI/사용자) 입력이 그대로 반환되는 경로는 존재하지 않는다.
    private String installStepFor(BuildRunStrategy strategy) {
        return switch (strategy) {
            case PNPM_BUILD, PNPM_INSTALL, PNPM_START -> "pnpm install --frozen-lockfile";
            case YARN_BUILD, YARN_INSTALL, YARN_START -> "yarn install --frozen-lockfile";
            default -> "npm ci";
        };
    }

    private String buildStepFor(BuildRunStrategy strategy) {
        return switch (strategy) {
            case NPM_BUILD -> "npm run build";
            case PNPM_BUILD -> "pnpm run build";
            case YARN_BUILD -> "yarn build";
            case MAVEN_PACKAGE -> "mvn -B package -DskipTests";
            case GRADLE_BUILD -> "./gradlew build -x test --no-daemon";
            case GRADLE_BOOT_JAR -> "./gradlew bootJar --no-daemon";
            case NONE, COPY_SOURCE -> "true";
            default -> throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        };
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String quoteJsonArray(String[] parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('"').append(parts[i].replace("\"", "\\\"")).append('"');
        }
        return sb.toString();
    }
}
