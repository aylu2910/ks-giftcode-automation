# ================================
# Stage 1 — Build the jar
# ================================
FROM gradle:8.14-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon -x test

# ================================
# Stage 2 — Runtime with Playwright
# ================================
FROM mcr.microsoft.com/playwright/java:v1.44.0-jammy

# Install Java 21
RUN apt-get update && apt-get install -y \
    openjdk-21-jre-headless \
    curl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
ENV PATH="$JAVA_HOME/bin:$PATH"

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

RUN mkdir -p /app/data

EXPOSE 8080

ENTRYPOINT ["java", \
    "-Xmx512m", \
    "-Xms256m", \
    "-Dspring.profiles.active=prod", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]