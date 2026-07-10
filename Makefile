MAKEFLAGS += -j6

.PHONY: all springboot springboot-tomcat quarkus go java-pure java-raw-nio clean

all: springboot springboot-tomcat quarkus go java-pure java-raw-nio

springboot:
	cd springboot && ./gradlew bootJar -q && docker build -t scale-springboot .

springboot-tomcat:
	cd springboot-tomcat && ./gradlew bootJar -q && docker build -t scale-springboot-tomcat .

quarkus:
	cd quarkus && ./mvnw package -DskipTests -q && docker build -t scale-quarkus .

go:
	cd go && docker build -t scale-go .

java-pure:
	cd java-pure && docker build -t scale-java-pure .

java-raw-nio:
	cd java-raw-nio && docker build -t scale-java-raw-nio .

clean:
	cd springboot && ./gradlew clean -q
	cd springboot-tomcat && ./gradlew clean -q
	cd quarkus && ./mvnw clean -q
	rm -rf java-pure/out java-raw-nio/out
	docker rmi -f scale-springboot scale-springboot-tomcat scale-quarkus scale-go scale-java-pure scale-java-raw-nio 2>/dev/null || true
