FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /app

# POM-only layer keeps the dependency cache across source-only changes.
COPY pom.xml ./
COPY statement-service/pom.xml ./statement-service/
COPY config-server/pom.xml ./config-server/

RUN mvn -pl config-server -am dependency:go-offline -B

COPY config-server/src ./config-server/src

RUN mvn -B -DskipTests package -pl config-server -am

FROM eclipse-temurin:25-jre
WORKDIR /app

# curl & jq: entrypoint's Vault AppRole login.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl jq ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Dedicated, unprivileged user - this process holds a Vault token and must not run as root.
RUN groupadd --gid 10002 config-server \
    && useradd --uid 10002 --gid config-server --no-create-home --shell /usr/sbin/nologin config-server

COPY --from=build --chown=config-server:config-server /app/config-server/target/*.jar /app/app.jar
COPY --chown=config-server:config-server --chmod=755 config-server/docker/config-server-entrypoint.sh /app/config-server-entrypoint.sh

USER config-server

# Self-sufficient outside docker-compose too (compose's healthcheck block takes precedence).
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=6 \
    CMD curl -fsS http://localhost:8888/actuator/health || exit 1

ENTRYPOINT ["/app/config-server-entrypoint.sh"]
CMD ["java","-jar","/app/app.jar"]
