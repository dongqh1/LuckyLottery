FROM maven:3.9.9-eclipse-temurin-11 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl user-service -am package -Dmaven.test.skip=true

FROM eclipse-temurin:11-jre
ENV TZ=Asia/Shanghai
WORKDIR /app
COPY --from=build /workspace/user-service/target/*.jar /app/app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
