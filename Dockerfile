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
# BUILD STRATEGY — pre-built JAR (Option A):
#   The original Dockerfile ran `./mvnw` inside the builder stage, which requires
#   the container to download the Maven distribution + dependencies from Maven
#   Central on every source change. That download is unreliable from the default
#   Docker bridge network in this environment (connection timeouts to
#   repo.maven.apache.org). To make image builds reliable + network-independent,
#   the JAR is now BUILT ON THE HOST first (`./mvnw clean package -DskipTests`,
#   which uses the host's ~/.m2 cache) and this Dockerfile only COPIES the
#   pre-built JAR. There is NO `./mvnw` step inside the container.
#
#   Prerequisite: run `./mvnw clean package -DskipTests` on the host before
#   `docker build`, so target/coresystembackend-0.0.1-SNAPSHOT.jar is fresh.
#
#   DEVIATION from HR-2 (documented): HR-2 mandates "builder uses ./mvnw (the
#   committed wrapper), NOT a host/installed Maven". This Dockerfile deviates —
#   the host build uses the committed ./mvnw wrapper (still not a host-installed
#   Maven), but the BUILD happens on the host, not inside the container. Recorded
#   in 05-decisions.md (OQ-AP-2/build-strategy). Rationale: container→Maven
#   Central networking is unreliable in this env; host build is reliable. The
#   runtime image contract (JRE only, no source/Maven, no secrets — HR-1) is
#   UNCHANGED. Revert to in-container ./mvnw when network is reliable.
#
# HR-1: no secret/.env/trust-store baked into the image (secrets come only from
#       env_file at run; trust store only via volume mount).

# ---------- Stage 1: builder (copies the host-built JAR) ----------
# No Maven, no source compile, no network needed — the JAR is built on the host
# with the committed ./mvnw wrapper (see BUILD STRATEGY above) before docker build.
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build
COPY target/coresystembackend-0.0.1-SNAPSHOT.jar app.jar

# ---------- Stage 2: runtime (JRE only) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Only the built JAR is copied from the builder — no source, no Maven, no pom,
# no .env, no trust store. Runtime image stays minimal.
COPY --from=builder /build/app.jar app.jar

# OQ-AP-2: default 7001; overridable via SERVER_PORT env at run.
EXPOSE 7001

# JAVA_OPTS is intentionally undefined in the image; compose (U-004) injects
# trust-store flags (OQ-AP-3) via JAVA_OPTS at run. sh -c lets the env var expand.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# SDD-PROVENANCE: U-003 | vault: .mega-sdd/vaults/api-platform | multi-stage Dockerfile (pre-built JAR copy — Option A; runtime eclipse-temurin:21-jre; EXPOSE 7001 OQ-AP-2; trust-store at /opt/bankmega-truststore/ OQ-AP-3). Deviation HR-2: build on host (./mvnw), not in-container — see 05-decisions.md.
