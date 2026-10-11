FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN set -eux; \
    for i in 1 2 3 4 5; do \
        mvn -B -ntp \
            -DskipTests \
            -Dmaven.wagon.http.retryHandler.count=5 \
            -Dmaven.wagon.http.retryHandler.requestSentEnabled=true \
            package && break; \
        if [ "$i" = "5" ]; then \
            echo "Maven build failed after 5 attempts"; \
            exit 1; \
        fi; \
        echo "Maven Central temporarily rate-limited the build. Retrying in 30 seconds..."; \
        sleep 30; \
    done

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "app.jar"]
