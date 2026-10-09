FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q dependency:copy-dependencies
COPY src src
RUN mvn -q package
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/target/classes ./classes
COPY --from=build /src/target/dependency ./lib
USER 65534:65534
EXPOSE 8080
CMD ["java","-cp","classes:lib/*","Main"]
