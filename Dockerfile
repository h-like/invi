# Packaged deployment: ship compiled assets and a boot JAR, never a Vite/dev server.
# APP_MODE picks the frontend build and the Spring profile together: https-test (default) or prod.
# Build args are public. Never pass a secret here; secrets are runtime environment variables only.
ARG APP_MODE=https-test

FROM node:22-alpine AS frontend
ARG APP_MODE
# Public operator contact shown on the privacy policy; required by the prod build (vite.config.ts).
ARG VITE_CONTACT_EMAIL=
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN case "$APP_MODE" in https-test|prod) ;; *) echo "APP_MODE must be https-test or prod" >&2; exit 1 ;; esac     && VITE_CONTACT_EMAIL="$VITE_CONTACT_EMAIL" npm run "build:$APP_MODE"

FROM eclipse-temurin:21-jdk-jammy AS backend
WORKDIR /build/backend
COPY backend/gradlew backend/build.gradle backend/settings.gradle ./
COPY backend/gradle/ ./gradle/
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY backend/src/main/ ./src/main/
# TemplateSeeder's presets come from the frontend theme registry (backend/build.gradle).
COPY frontend/src/editor/themes.json /build/frontend/src/editor/themes.json
COPY --from=frontend /build/frontend/dist/ ./src/main/resources/static/
# Tests run before checkpoint/push and in CI with Postgres, not against a deployment DB.
RUN ./gradlew --no-daemon bootJar -x test --console=plain \
    && cp build/libs/*-SNAPSHOT.jar /build/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime
ARG APP_MODE
RUN groupadd --gid 10001 invi \
    && useradd --uid 10001 --gid invi --create-home invi
WORKDIR /app
COPY --from=backend --chown=invi:invi /build/app.jar /app/app.jar
USER invi
# Public memory tuning only. Never put secrets in JAVA_TOOL_OPTIONS or image build args.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55.0 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=32m -Xss512k -XX:ActiveProcessorCount=2 -XX:+ExitOnOutOfMemoryError"
# The profile matches the frontend this image was built with.
ENV SPRING_PROFILES_ACTIVE=${APP_MODE}
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
