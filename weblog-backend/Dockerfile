# ===== Stage 1: Build =====
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY weblog-common/pom.xml weblog-common/
COPY weblog-infra-redis/pom.xml weblog-infra-redis/
COPY weblog-infra-oss/pom.xml weblog-infra-oss/
COPY weblog-infra-lucene/pom.xml weblog-infra-lucene/
COPY weblog-infra-security/pom.xml weblog-infra-security/
COPY weblog-module-system/pom.xml weblog-module-system/
COPY weblog-module-content/pom.xml weblog-module-content/
COPY weblog-module-interaction/pom.xml weblog-module-interaction/
COPY weblog-api/pom.xml weblog-api/
# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B
COPY . .
RUN mvn clean package -DskipTests -B

# ===== Stage 2: Runtime =====
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -g 1000 app && adduser -u 1000 -G app -D app
WORKDIR /app
COPY --from=builder /build/weblog-api/target/weblog-api-*.jar app.jar
RUN mkdir -p /var/log/weblog /data/lucene && chown -R app:app /var/log/weblog /data/lucene /app
USER app
EXPOSE 9091
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:9091/actuator/health || exit 1
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar", "--spring.profiles.active=prod"]
