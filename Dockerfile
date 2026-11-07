# ---------- Backend image (Spring Boot, Java 21) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Cache dependencies in their own layer
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests need Docker (Testcontainers), so they are not run inside the image build
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER app

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -Xss512k"

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
