# =============================================================================
# RER DSP — job-data-migration (dsp-batch)
# Build context: this repository root
# =============================================================================

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn/ .mvn/
COPY src/ ./src/

RUN chmod +x ./mvnw \
    && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN apt-get update && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean

COPY --from=build /app/target/dsp-batch-*.jar /app/app.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" \
    SPRING_MAIN_WEB_APPLICATION_TYPE=none

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
