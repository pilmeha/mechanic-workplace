FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY backend/settings.gradle.kts ./settings.gradle.kts
COPY backend/build.gradle.kts ./build.gradle.kts
COPY backend/src ./src
RUN gradle installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/install/backend /app
EXPOSE 8080
ENTRYPOINT ["/app/bin/backend"]
