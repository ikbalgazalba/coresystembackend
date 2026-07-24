# Dockerfile — coresystembackend (api-platform vault, U-003)
#
# Decisions / OQ resolutions:
#   - OQ-AP-2 (container port): default 7001 (application-prod.yaml sets
#     server.port=${SERVER_PORT:7001}). EXPOSE 7001 below. Override at run via SERVER_PORT env.
#   - OQ-AP-3 (trust-store mount target inside container):
#     /opt/bankmega-truststore/bankmega-truststore.p12
#     The compose unit (U-004) bind-mounts the host trust store to this path and
#     sets JAVA_OPTS=-Djavax.net.ssl.trustStore=/opt/bankmega-truststore/bankmega-truststore.p12
#     (plus -Djavax.net.ssl.trustStorePassword=...). No trust store is baked into
#     this image — it is injected purely as a runtime volume + env (HR-1, §D-004).
#
#   - curl caveat: the eclipse-temurin:21-jre runtime image ships no curl/wget by
#     default. This Dockerfile therefore ships NO HEALTHCHECK. The healthcheck
#     mechanism (Java-based / CMD-SHELL probe, or installing curl) is U-004's
#     decision via docker-compose.yml — do not assume curl exists here.
#
# HR-1: no secret/.env/trust-store baked into the image (secrets come only from
#       env_file at run; trust store only via volume mount).
# HR-2: runtime stage is eclipse-temurin:21-jre (JRE only); builder uses ./mvnw
#       (the committed wrapper), not a host-installed Maven.

# ---------- Stage 1: builder (JDK + Maven wrapper) ----------
# Needs network to repo.spring.io/snapshot (Spring Boot 4 parent) and Maven
# Central (springdoc 3.0.3, actuator, etc.). This is expected.
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

# Layer-caching: least-frequently-changing files first.
COPY pom.xml .
COPY mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY src src

# Build the executable JAR using the committed wrapper. Tests run as a separate
# gate (§D-005), so -DskipTests keeps the image build fast + reproducible.
RUN chmod +x mvnw && ./mvnw -B clean package -DskipTests

# ---------- Stage 2: runtime (JRE only) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Only the built JAR is copied from the builder — no source, no Maven, no pom,
# no .env, no trust store. Runtime image stays minimal.
COPY --from=builder /build/target/coresystembackend-0.0.1-SNAPSHOT.jar app.jar

# OQ-AP-2: default 7001; overridable via SERVER_PORT env at run.
EXPOSE 7001

# JAVA_OPTS is intentionally undefined in the image; compose (U-004) injects
# trust-store flags (OQ-AP-3) via JAVA_OPTS at run. sh -c lets the env var expand.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# SDD-PROVENANCE: U-003 | vault: .mega-sdd/vaults/api-platform | multi-stage Dockerfile (eclipse-temurin:21-jdk builder + 21-jre runtime; ./mvnw per HR-2; EXPOSE 7001 OQ-AP-2; trust-store at /opt/bankmega-truststore/ OQ-AP-3)
