# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# The Gradle wrapper and settings live at the repository root. This Dockerfile
# must therefore be built with the repository root as its build context:
# docker build -f backend/Dockerfile .
COPY gradlew settings.gradle ./
COPY gradle ./gradle
COPY backend/build.gradle ./backend/build.gradle
COPY backend/src ./backend/src

RUN ./gradlew :backend:bootJar --no-daemon

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
