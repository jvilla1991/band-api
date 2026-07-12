# ── Build stage ────────────────────────────────────────────────────────────────
# Build the JAR inside Docker with the Maven wrapper so the image is self-contained.
# Tests are SKIPPED here on purpose: BandApiApplicationTests uses Testcontainers, which
# needs a Docker daemon the image build doesn't have. CI runs `./mvnw test` separately.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn/
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B -q

COPY src src/
RUN ./mvnw -DskipTests package -B -q

# ── Runtime stage ───────────────────────────────────────────────────────────────

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
