# Multi-stage build: compile API fat-jar, run on JRE 17 (Jammy — libstdc++ for ONNX native libs).
# Build context must be the ai-rts/ directory (see render.yaml).

FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
RUN apt-get update && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
COPY . .
RUN mvn -B -ntp -pl api -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system airts && useradd --system --gid airts airts
USER airts
COPY --from=build /workspace/api/target/api-0.1.0-SNAPSHOT.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${PORT:-8080}/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
