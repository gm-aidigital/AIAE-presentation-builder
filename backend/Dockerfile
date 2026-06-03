# Dockerfile — local-dev / handoff only. Backend-only image (no frontend build).
# Java 21 must match backend/pom.xml `<java.version>` + replit.nix `pkgs.jdk21`.
# Replit Deployment uses replit-build.sh to package the SPA into the Spring jar.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/ ./backend/
RUN mvn -f backend/pom.xml -B -DskipTests -Dskip.frontend=true package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/backend/application/target/*.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
  CMD sh -c 'curl -fsS "http://localhost:${PORT:-8080}${APP_CONTEXT_PATH:-}/actuator/health" || exit 1'
ENV SPRING_PROFILES_ACTIVE=local
ENV PORT=8080
ENV JAVA_OPTS=""
ENTRYPOINT exec java -Duser.timezone=UTC -Xmx1G $JAVA_OPTS -jar /app/app.jar
