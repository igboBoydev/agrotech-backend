# FarmIQ backend (An-Agrotech-application) — multi-stage build.
#
# Build:   docker build -t farmiq-app .
# Run:     docker compose up   (see docker-compose.yml — this image alone needs a
#          Postgres reachable at DB_HOST/DB_PORT and the env vars in .env.example)

# ---- Build stage ----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copy just the POM first and resolve dependencies as their own layer, so a
# source-only change doesn't bust the (slow) dependency-download cache.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package \
    && cp target/*.jar app.jar

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime

# curl only for the HEALTHCHECK below — mirrors what a Kubernetes liveness/
# readiness probe would hit at /actuator/health.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system farmiq && useradd --system --gid farmiq --no-create-home farmiq

WORKDIR /app
COPY --from=build /build/app.jar app.jar
RUN chown -R farmiq:farmiq /app
USER farmiq

# gRPC rides Tomcat's server.port, not a separate port (confirmed this session —
# the grpc.server.port/GRPC_PORT config key is dead, see application-*.yaml
# comments) — only one port to expose.
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JAVA_OPTS is empty by default — set it at `docker run`/compose/k8s-manifest
# time (e.g. -Xmx512m) without rebuilding the image.
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
