FROM maven:3.8-openjdk-17 AS builder
WORKDIR /build
COPY . .
RUN mvn clean package -pl search-service -am -DskipTests -q

FROM openjdk:17-jdk-alpine
WORKDIR /app
COPY --from=builder /build/search-service/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=dev"]
