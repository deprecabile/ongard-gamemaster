# Multi-stage build for Spring Boot application
FROM maven:3.9-eclipse-temurin-25-alpine AS build

WORKDIR /app

# Install ondgard-core: use pre-built jar if available, otherwise build from source
COPY ondgard-core /app/ondgard-core
RUN --mount=type=cache,target=/root/.m2 \
    if ls /app/ondgard-core/target/ondgard-core-*.jar 1>/dev/null 2>&1; then \
      for jar_file in /app/ondgard-core/target/ondgard-core-*.jar; do \
        mvn install:install-file \
          -Dfile="$jar_file" \
          -DpomFile=/app/ondgard-core/pom.xml -B; \
      done; \
    else \
      mvn -f /app/ondgard-core/pom.xml clean install -DskipTests -Dformatter.skip -B; \
    fi

# Copy pom.xml and source code
COPY ondgard-auth/pom.xml ./
COPY ondgard-auth/src ./src

# Build the application
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests -Dformatter.skip -B

# Runtime stage
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the application port
EXPOSE 8089

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
