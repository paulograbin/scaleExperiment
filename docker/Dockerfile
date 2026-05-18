FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

COPY build/libs/scale-experiment-1.0.0.jar app.jar

ENV JAVA_OPTS="-XX:+UseZGC \
  -XX:+ZGenerational \
  -Xms512m \
  -Xmx512m \
  -XX:+AlwaysPreTouch \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.jmx.enabled=false"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
