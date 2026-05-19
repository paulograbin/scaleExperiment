MAKEFLAGS += -j3

.PHONY: all springboot quarkus go clean

all: springboot quarkus go

springboot:
	cd springboot && ./gradlew bootJar -q && docker build -t scale-springboot .

quarkus:
	cd quarkus && ./mvnw package -DskipTests -q && docker build -t scale-quarkus .

go:
	cd go && docker build -t scale-go .

clean:
	cd springboot && ./gradlew clean -q
	cd quarkus && ./mvnw clean -q
	docker rmi -f scale-springboot scale-quarkus scale-go 2>/dev/null || true
