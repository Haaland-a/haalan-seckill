FROM openjdk:17-jre-slim
WORKDIR /app
COPY target/item-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=dev"]