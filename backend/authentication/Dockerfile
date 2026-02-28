# Multi-stage build for Spring Boot application
FROM maven:3.9-eclipse-temurin-25-alpine AS build

WORKDIR /app

# Install ongard-core: use pre-built jar if available, otherwise build from source
COPY ongard-core /app/ongard-core
RUN --mount=type=cache,target=/root/.m2 \
    if ls /app/ongard-core/target/ongard-core-*.jar 1>/dev/null 2>&1; then \
      for jar_file in /app/ongard-core/target/ongard-core-*.jar; do \
        mvn install:install-file \
          -Dfile="$jar_file" \
          -DpomFile=/app/ongard-core/pom.xml -B; \
      done; \
    else \
      mvn -f /app/ongard-core/pom.xml clean install -DskipTests -B; \
    fi

# Copy pom.xml and source code
COPY authentication/pom.xml ./
COPY authentication/src ./src

# Build the application
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the application port
EXPOSE 8089

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
