# Built from the executable jar Maven packages: ./mvnw -pl ride-service -am package -DskipTests
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /build
COPY target/ride-service-*-exec.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ride && adduser -S ride -G ride
WORKDIR /app
# dependencies change least often, so they come first and stay cached between builds
COPY --from=layers /build/extracted/dependencies/ ./
COPY --from=layers /build/extracted/spring-boot-loader/ ./
COPY --from=layers /build/extracted/snapshot-dependencies/ ./
COPY --from=layers /build/extracted/application/ ./
USER ride
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
