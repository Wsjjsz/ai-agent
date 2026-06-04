FROM public.ecr.aws/amazoncorretto/amazoncorretto:21

WORKDIR /app

COPY target/ai-agent-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8123

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
