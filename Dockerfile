# Dockerfile — coresystembackend with truststore + real .env
# Reuse existing cached base image (Docker Hub unreachable)
FROM coresystembackend:latest
WORKDIR /app
COPY target/coresystembackend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 7001
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djavax.net.ssl.trustStore=/opt/bankmega-truststore/bankmega-truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit -Djavax.net.ssl.trustStoreType=PKCS12 -jar app.jar --spring.profiles.active=prod"]
