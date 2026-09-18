# Build stage: reproducible Maven build with pinned toolchain images.
# Tests run in the Maven/Tescontainers pipeline, not inside the image build.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp -DskipTests clean package

# Runtime stage: JRE only, non-root, no durable local business data.
# Every credential and endpoint comes from environment at runtime.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd --system --uid 1001 kbase
COPY --from=build /build/target/kbase-backend-0.1.0-SNAPSHOT.jar /app/kbase-backend.jar
USER kbase
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/kbase-backend.jar"]
