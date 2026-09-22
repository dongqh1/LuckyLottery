FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl marketing-service -am package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre
ENV TZ=Asia/Shanghai
WORKDIR /app
COPY --from=build /workspace/marketing-service/target/luckyhub-0.0.1-SNAPSHOT.jar /app/marketing-service.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/marketing-service.jar"]
