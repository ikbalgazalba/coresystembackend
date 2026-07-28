# Dockerfile — reuse existing coresystembackend:latest base (Docker Hub unreachable)
# The old image has eclipse-temurin:21-jre layers already cached.
FROM coresystembackend:latest
WORKDIR /app
COPY target/coresystembackend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-local}"]
