FROM btgoose/jdk17:latest
WORKDIR /app
COPY cart-service/target/cart-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=dev"]
