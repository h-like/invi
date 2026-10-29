# HTTPS test deployment: ship compiled assets and a boot JAR, never a Vite/dev server.
FROM node:22-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build:https-test

FROM eclipse-temurin:21-jdk-jammy AS backend
WORKDIR /build/backend
COPY backend/gradlew backend/build.gradle backend/settings.gradle ./
COPY backend/gradle/ ./gradle/
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY backend/src/main/ ./src/main/
COPY --from=frontend /build/frontend/dist/ ./src/main/resources/static/
# Tests run before checkpoint/push and in CI with Postgres, not against a deployment DB.
RUN ./gradlew --no-daemon bootJar -x test --console=plain \
    && cp build/libs/*-SNAPSHOT.jar /build/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd --gid 10001 invi \
    && useradd --uid 10001 --gid invi --create-home invi
WORKDIR /app
COPY --from=backend --chown=invi:invi /build/app.jar /app/app.jar
USER invi
# Public memory tuning only. Never put secrets in JAVA_TOOL_OPTIONS or image build args.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55.0 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=32m -Xss512k -XX:ActiveProcessorCount=2 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=https-test"]
