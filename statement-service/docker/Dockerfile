# stage 1: build with Maven
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Copy the parent POM
COPY pom.xml ./

# Copy all module POMs (for dependency resolution)
COPY statement-service/pom.xml ./statement-service/
COPY config-server/pom.xml ./config-server/

# Copy only statement-service source
COPY statement-service/src ./statement-service/src

# Build statement-service
RUN mvn -DskipTests clean package -pl statement-service -am

# stage 2: runtime
FROM eclipse-temurin:25-jre
WORKDIR /app

# Install curl for health checks
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/statement-service/target/*.jar /app/app.jar

# copy scripts and entrypoint (paths relative to build context: statement-service-platform/)
COPY statement-service/docker/wait-for-config.sh /app/wait-for-config.sh
COPY statement-service/docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/wait-for-config.sh /app/entrypoint.sh

ENTRYPOINT [ "/app/entrypoint.sh" ]
