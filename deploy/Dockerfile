FROM eclipse-temurin:17.0.14_7-jre

WORKDIR /app

ENV TZ=Asia/Shanghai

RUN mkdir -p /app/data /app/logs

ARG JAR_FILE=hellodoc.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseG1GC", "-Djava.security.egd=file:/dev/./urandom", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
