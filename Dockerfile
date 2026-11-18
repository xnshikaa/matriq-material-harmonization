FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/harmonizer-0.1.0.jar /app/matriq.jar
ENV MATRIQ_BIND_ADDRESS=0.0.0.0
ENV MATRIQ_STATE_DIRECTORY=/app/storage
ENV MATRIQ_SECURE_COOKIE=true
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/matriq.jar"]
