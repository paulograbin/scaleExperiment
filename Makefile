MAKEFLAGS += -j4

.PHONY: all springboot quarkus go java-pure clean

all: springboot quarkus go java-pure

springboot:
	cd springboot && ./gradlew bootJar -q && docker build -t scale-springboot .

quarkus:
	cd quarkus && ./mvnw package -DskipTests -q && docker build -t scale-quarkus .

go:
	cd go && docker build -t scale-go .

java-pure:
	cd java-pure && docker build -t scale-java-pure .

clean:
	cd springboot && ./gradlew clean -q
	cd quarkus && ./mvnw clean -q
	rm -rf java-pure/out
	docker rmi -f scale-springboot scale-quarkus scale-go scale-java-pure 2>/dev/null || true
