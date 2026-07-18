package gj.cloud.ops.application.deployment.spec;

import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.springframework.stereotype.Component;

// D-1 "제공 템플릿" 6종에 대한 표준 멀티스테이지 Dockerfile 생성.
// 참고: 실제 이미지 빌드로 검증한 템플릿이 아니라 각 스택의 통상적인 Dockerfile 관례를 따른 것 — 실제 배포 전 확인 권장.
@Component
public class DockerfileGenerator {

    public String generate(ServiceSpec service) {
        return switch (service.runtime()) {
            case "spring-boot" -> springBoot(service);
            case "nextjs" -> nextjs(service);
            case "react-nginx" -> reactNginx(service);
            case "nodejs" -> nodejs(service);
            case "nestjs" -> nestjs(service);
            case "python" -> python(service);
            default -> throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        };
    }

    private String springBoot(ServiceSpec service) {
        int javaVersion = service.javaVersion() != null ? service.javaVersion() : 21;
        boolean maven = "maven".equalsIgnoreCase(service.buildTool());
        String buildCmd = maven ? "mvn -B package -DskipTests" : "./gradlew build -x test --no-daemon";
        String jarGlob = maven ? "/app/target/*.jar" : "/app/build/libs/*.jar";

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
                """.formatted(javaVersion, buildCmd, javaVersion, jarGlob, service.containerPort());
    }

    private String nextjs(ServiceSpec service) {
        int nodeVersion = service.nodeVersion() != null ? service.nodeVersion() : 20;
        String buildCommand = service.buildCommand() != null ? service.buildCommand() : "npm run build";

        return """
                FROM node:%d-alpine AS build
                WORKDIR /app
                COPY package*.json ./
                RUN npm ci
                COPY . .
                RUN %s

                FROM node:%d-alpine
                WORKDIR /app
                ENV NODE_ENV=production
                COPY --from=build /app ./
                EXPOSE %d
                CMD ["npm", "start"]
                """.formatted(nodeVersion, buildCommand, nodeVersion, service.containerPort());
    }

    // nginx가 80으로 서빙하는 게 표준 관례라 containerPort 지정과 무관하게 80 고정 (compose 쪽에서도 동일하게 처리해야 함)
    private String reactNginx(ServiceSpec service) {
        int nodeVersion = service.nodeVersion() != null ? service.nodeVersion() : 20;
        String buildCommand = service.buildCommand() != null ? service.buildCommand() : "npm run build";

        return """
                FROM node:%d-alpine AS build
                WORKDIR /app
                COPY package*.json ./
                RUN npm ci
                COPY . .
                RUN %s

                FROM nginx:alpine
                COPY --from=build /app/build /usr/share/nginx/html
                EXPOSE 80
                """.formatted(nodeVersion, buildCommand);
    }

    private String nodejs(ServiceSpec service) {
        int nodeVersion = service.nodeVersion() != null ? service.nodeVersion() : 20;
        String startCommand = service.startCommand() != null ? service.startCommand() : "node index.js";
        String[] cmdParts = startCommand.split("\\s+");
        String cmdArray = quoteJsonArray(cmdParts);

        return """
                FROM node:%d-alpine
                WORKDIR /app
                COPY package*.json ./
                RUN npm ci --omit=dev
                COPY . .
                EXPOSE %d
                CMD [%s]
                """.formatted(nodeVersion, service.containerPort(), cmdArray);
    }

    private String nestjs(ServiceSpec service) {
        int nodeVersion = service.nodeVersion() != null ? service.nodeVersion() : 20;

        return """
                FROM node:%d-alpine AS build
                WORKDIR /app
                COPY package*.json ./
                RUN npm ci
                COPY . .
                RUN npm run build

                FROM node:%d-alpine
                WORKDIR /app
                ENV NODE_ENV=production
                COPY --from=build /app/dist ./dist
                COPY --from=build /app/node_modules ./node_modules
                EXPOSE %d
                CMD ["node", "dist/main"]
                """.formatted(nodeVersion, nodeVersion, service.containerPort());
    }

    private String python(ServiceSpec service) {
        String pythonVersion = service.pythonVersion() != null ? service.pythonVersion() : "3.11";
        String framework = service.pythonFramework() != null ? service.pythonFramework() : "fastapi";
        String cmd = switch (framework) {
            case "django" -> "[\"python\", \"manage.py\", \"runserver\", \"0.0.0.0:%d\"]".formatted(service.containerPort());
            case "flask" -> "[\"flask\", \"run\", \"--host=0.0.0.0\", \"--port=%d\"]".formatted(service.containerPort());
            default -> "[\"uvicorn\", \"main:app\", \"--host\", \"0.0.0.0\", \"--port\", \"%d\"]".formatted(service.containerPort());
        };

        return """
                FROM python:%s-slim
                WORKDIR /app
                COPY requirements.txt .
                RUN pip install --no-cache-dir -r requirements.txt
                COPY . .
                EXPOSE %d
                CMD %s
                """.formatted(pythonVersion, service.containerPort(), cmd);
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
