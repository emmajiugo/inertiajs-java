# Build from project root: docker build -f examples/example-spring/Dockerfile -t inertia-example .

# Stage 1: Build frontend assets
FROM node:22-slim AS frontend
WORKDIR /frontend
COPY examples/example-spring/frontend/package.json examples/example-spring/frontend/package-lock.json* ./
RUN npm install
COPY examples/example-spring/frontend/ ./
RUN npm run build
# Output: /src/main/resources/static/ (outDir is ../src/main/resources/static)

# Stage 2: Build backend
FROM eclipse-temurin:17-jdk AS backend
WORKDIR /app
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY inertiajs-core inertiajs-core
COPY inertiajs-spring inertiajs-spring
COPY examples examples
COPY --from=frontend /src/main/resources/static/ examples/example-spring/src/main/resources/static/
RUN chmod +x gradlew && ./gradlew :examples:example-spring:bootJar --no-daemon

# Stage 3: Minimal runtime
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend /app/examples/example-spring/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
