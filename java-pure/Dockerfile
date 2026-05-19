FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY src/ src/
RUN javac -d out src/Server.java

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /app/out/ .

ENV JAVA_OPTS="-XX:+UseZGC \
  -XX:+ZGenerational \
  -Xms512m \
  -Xmx512m \
  -XX:+AlwaysPreTouch \
  -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS com.paulograbin.scale.Server"]
