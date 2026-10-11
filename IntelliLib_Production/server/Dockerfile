FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

# Render's shared build IP can be throttled by repo.maven.apache.org.
# Google Cloud provides a hosted mirror of Maven Central, so use it for
# deterministic cloud builds while keeping the same Maven artifacts.
RUN mkdir -p /root/.m2 && printf '%s\n' \
  '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' \
  '          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' \
  '          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' \
  '  <mirrors>' \
  '    <mirror>' \
  '      <id>google-maven-central</id>' \
  '      <name>Google Cloud Maven Central mirror</name>' \
  '      <url>https://maven-central.storage-download.googleapis.com/maven2/</url>' \
  '      <mirrorOf>central</mirrorOf>' \
  '    </mirror>' \
  '  </mirrors>' \
  '</settings>' > /root/.m2/settings.xml

COPY pom.xml .
COPY src ./src

RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "app.jar"]
