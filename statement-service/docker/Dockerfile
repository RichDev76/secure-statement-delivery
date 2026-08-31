# stage 1: build with Maven
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /app

# Copy the parent POM
COPY pom.xml ./

# Copy all module POMs (for dependency resolution)
COPY statement-service/pom.xml ./statement-service/
COPY config-server/pom.xml ./config-server/

# Resolve dependencies (and build plugins) in their own layer, keyed only on the POMs above -
# a source-only change no longer busts the download cache on rebuild. dependency:go-offline alone
# misses plugins that resolve their own engine deps at execution time rather than declaring them
# as POM dependencies (Spotless's formatter here) - spotless:check on an empty tree still resolves
# and caches those, it just has no files to check yet.
RUN mvn -pl statement-service -am dependency:go-offline -B \
    && mvn -pl statement-service -am spotless:check -B

# Copy only statement-service source
COPY statement-service/src ./statement-service/src

# Build statement-service
RUN mvn -DskipTests clean package -pl statement-service -am

# stage 2: runtime
FROM eclipse-temurin:25-jre
WORKDIR /app

# curl: health checks. Pebble is unused; drop it and its CVE surface.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* /usr/bin/pebble

# Dedicated, unprivileged user - the JVM never needs root, and this is a security-sensitive service
RUN groupadd --gid 10001 statement-service \
    && useradd --uid 10001 --gid statement-service --no-create-home --shell /usr/sbin/nologin statement-service

COPY --from=build /app/statement-service/target/*.jar /app/app.jar

# copy scripts and entrypoint (paths relative to build context: statement-service-platform/)
COPY statement-service/docker/wait-for-config.sh /app/wait-for-config.sh
COPY statement-service/docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/wait-for-config.sh /app/entrypoint.sh \
    && chown -R statement-service:statement-service /app

USER statement-service

EXPOSE 8080

# Self-sufficient outside docker-compose too (compose's own healthcheck block, when present,
# takes precedence over this - they're not redundant, this is the fallback).
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
    CMD curl -fsS http://localhost:8080/api/v1/statements/actuator/health/liveness || exit 1

ENTRYPOINT [ "/app/entrypoint.sh" ]
