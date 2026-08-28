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

# supercronic: Unix crontab in continuous mode (core mounts the entrypoint).
# jq + postgresql-client: option 3 publishes GeoServer layers after the first load
# (same populate_geoserver.sh used by ./setup.sh, called over the Docker network).
# Pinned: https://github.com/aptible/supercronic/releases/tag/v0.2.49
ARG TARGETARCH
ENV SUPERCRONIC_VERSION=v0.2.49

RUN apt-get update && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends ca-certificates curl jq postgresql-client \
    && arch="${TARGETARCH:-$(dpkg --print-architecture)}" \
    && case "$arch" in \
         amd64|x86_64)  sc_arch=amd64; sc_sha=e63c11a9726b775a6a11801e81af4f3fb926aa68 ;; \
         arm64|aarch64) sc_arch=arm64; sc_sha=0b6c5bb743e0b0dafed1132198c81807927ac413 ;; \
         *) echo "Unsupported architecture for supercronic: $arch" >&2; exit 1 ;; \
       esac \
    && curl -fsSL -o /tmp/supercronic \
         "https://github.com/aptible/supercronic/releases/download/${SUPERCRONIC_VERSION}/supercronic-linux-${sc_arch}" \
    && echo "${sc_sha}  /tmp/supercronic" | sha1sum -c - \
    && chmod +x /tmp/supercronic \
    && mv /tmp/supercronic /usr/local/bin/supercronic \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean

COPY --from=build /app/target/dsp-batch-*.jar /app/app.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" \
    SPRING_MAIN_WEB_APPLICATION_TYPE=none

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
