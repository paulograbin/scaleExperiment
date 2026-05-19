FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

COPY target/quarkus-app/lib/ lib/
COPY target/quarkus-app/*.jar .
COPY target/quarkus-app/app/ app/
COPY target/quarkus-app/quarkus/ quarkus/

ENV JAVA_OPTS="-XX:+UseZGC \
  -XX:+ZGenerational \
  -Xms512m \
  -Xmx512m \
  -XX:+AlwaysPreTouch \
  -Xlog:gc \
  -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar quarkus-run.jar"]
