# ---- Build stage (JDK 25) ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Copy entire repository so Maven can resolve parent POMs and sibling modules
COPY . /workspace

# Build only the config-server module (and its dependencies)
RUN mvn -f pom.xml -pl config-server -am -B -DskipTests package

# ---- Runtime stage (JRE 25) ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the built jar produced by the build stage
COPY --from=build /workspace/config-server/target/*.jar /app/app.jar

# Install curl & jq so the entrypoint can call Vault API
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl jq ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Copy the entrypoint from the config-server/docker path (repo-root context)
COPY config-server/docker/config-server-entrypoint.sh /opt/config-server/config-server-entrypoint.sh
RUN chmod +x /opt/config-server/config-server-entrypoint.sh

ENTRYPOINT ["/opt/config-server/config-server-entrypoint.sh"]
CMD ["java","-jar","/app/app.jar"]
