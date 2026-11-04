FROM maven:3.10.0-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY venuepass-common venuepass-common
COPY venuepass-pojo venuepass-pojo
COPY venuepass-server venuepass-server
RUN mvn -B -pl venuepass-server -am package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/venuepass-server/target/venuepass-server-1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
