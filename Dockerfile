# Build
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon -q dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon -q bootJar -x test

# Run
FROM eclipse-temurin:25-jre
# curl for the healthcheck in deploy/docker-compose.yml (not part of the temurin 25 image).
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd --system lan && useradd --system --gid lan lan
WORKDIR /app
COPY --from=build /src/build/libs/lan-dashboard-backend.jar app.jar
ENV TZ=Europe/Zurich
USER lan
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
