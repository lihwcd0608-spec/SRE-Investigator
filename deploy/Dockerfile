# 控制面一键镜像：打进 Boot 的静态控制台，不在镜像里装 Node。
# 构建上下文必须是仓库根（compose 里 context: ..）：
#   docker compose -f deploy/docker-compose.yml --profile app up -d --build mysql redis app
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY sre-investigator-api/pom.xml sre-investigator-api/pom.xml
COPY sre-investigator-domain/pom.xml sre-investigator-domain/pom.xml
COPY sre-investigator-infra/pom.xml sre-investigator-infra/pom.xml
COPY sre-investigator-graph/pom.xml sre-investigator-graph/pom.xml
COPY sre-investigator-mcp-servers/pom.xml sre-investigator-mcp-servers/pom.xml
COPY sre-investigator-boot/pom.xml sre-investigator-boot/pom.xml
COPY sre-investigator-console/pom.xml sre-investigator-console/pom.xml
COPY sre-investigator-api sre-investigator-api
COPY sre-investigator-domain sre-investigator-domain
COPY sre-investigator-infra sre-investigator-infra
COPY sre-investigator-graph sre-investigator-graph
COPY sre-investigator-mcp-servers sre-investigator-mcp-servers
COPY sre-investigator-boot sre-investigator-boot
COPY sre-investigator-console sre-investigator-console
RUN mvn -B -pl sre-investigator-boot -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/sre-investigator-boot/target/sre-investigator-boot-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8790
ENV JAVA_OPTS="-Xms256m -Xmx512m -Duser.timezone=Asia/Shanghai"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
