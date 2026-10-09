# Build & jalankan backend (Java 21). Dipakai: docker compose --profile app up -d
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/erp-backend-*.jar /app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 erp && mkdir -p /data/files && chown erp /data/files
USER erp
COPY --from=build /app.jar /app.jar
ENV TZ=Asia/Jakarta ERP_FILES_DIR=/data/files
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app.jar"]
