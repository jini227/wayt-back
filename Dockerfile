FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY build/libs/*-SNAPSHOT.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx256m"

EXPOSE 19191

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
