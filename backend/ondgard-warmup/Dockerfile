FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /app
COPY ondgard-warmup/pom.xml ./
COPY ondgard-warmup/src ./src
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests -B

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
