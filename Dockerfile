FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /project
COPY . .
ARG MODULE
RUN mvn -B -pl "${MODULE}" -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
ARG MODULE
COPY --from=builder /project/${MODULE}/target/${MODULE}-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
