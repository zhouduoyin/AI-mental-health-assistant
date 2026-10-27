# 后端镜像：Maven 编译 -> 精简 JRE 运行
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache tzdata \
    && addgroup -S app \
    && adduser -S app -G app

WORKDIR /app
COPY --from=build /app/target/spring-ai-rag-0.0.1-SNAPSHOT.jar app.jar

ENV TZ=Asia/Shanghai \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
